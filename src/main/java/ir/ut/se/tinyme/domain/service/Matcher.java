package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.ListIterator;

@Service
public class Matcher {
    public MatchResult match(Order newOrder) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();

        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;

            Trade trade = new Trade(newOrder.getSecurity(), matchingOrder.getPrice(), Math.min(newOrder.getQuantity(), matchingOrder.getQuantity()), newOrder, matchingOrder);
            if (newOrder.getSide() == Side.BUY) {
                if (trade.buyerHasEnoughCredit())
                    trade.decreaseBuyersCredit();
                else {
                    rollbackTrades(newOrder, trades);
                    return MatchResult.notEnoughCredit();
                }
            }
            trade.increaseSellersCredit();
            trades.add(trade);

            if (newOrder.getQuantity() >= matchingOrder.getQuantity()) {
                newOrder.decreaseQuantity(matchingOrder.getQuantity());
                orderBook.removeFirst(matchingOrder.getSide());
                if (matchingOrder instanceof IcebergOrder icebergOrder) {
                    icebergOrder.decreaseQuantity(matchingOrder.getQuantity());
                    icebergOrder.replenish();
                    if (icebergOrder.getQuantity() > 0)
                        orderBook.enqueue(icebergOrder);
                }
            } else {
                matchingOrder.decreaseQuantity(newOrder.getQuantity());
                newOrder.makeQuantityZero();
            }
        }
        if (newOrder.getQuantity() > 0 && newOrder.getSide() == Side.BUY){
            if (!newOrder.getBroker().hasEnoughCredit((long)newOrder.getPrice() * newOrder.getQuantity())) {
                rollbackTrades(newOrder,trades);
                return MatchResult.notEnoughCredit();
            }
        }
        if(!trades.isEmpty()){
            newOrder.getSecurity().setLastTradePrice(trades.getLast().getPrice());
        }
        if (newOrder.getStatus() == OrderStatus.ACTIVE){
            return MatchResult.stopLimitOrderActivated (newOrder, trades);
        }
        return MatchResult.executed(newOrder, trades);
    }

    private LinkedList<MatchResult> checkAndActivateStopLimitOrderBook(Security security){
        // this part has a little bug I think
        LinkedList<MatchResult> results = new LinkedList<>();

        for (Order inactiveOrder : security.getStopLimitOrderList().getSellQueue()){
            if (inactiveOrder.getStopPrice() >= security.getLastTradePrice()) {
                security.getStopLimitOrderList().getSellQueue().remove(inactiveOrder);
                results.addAll(this.execute(Order.builder()
                        .orderId(inactiveOrder.getOrderId())
                        .security(security)
                        .side(inactiveOrder.getSide())
                        .quantity(inactiveOrder.getQuantity())
                        .price(inactiveOrder.getPrice())
                        .broker(inactiveOrder.getBroker())
                        .shareholder(inactiveOrder.getShareholder())
                        .entryTime(inactiveOrder.getEntryTime())
                        .status(OrderStatus.ACTIVE)
                        .build()
                ));
            }
        }
        for (Order inactiveOrder : security.getStopLimitOrderList().getBuyQueue()){
            if (inactiveOrder.getStopPrice() <= security.getLastTradePrice()) {
                security.getStopLimitOrderList().getBuyQueue().remove(inactiveOrder);
                inactiveOrder.getBroker().increaseCreditBy((long)inactiveOrder.getPrice() * inactiveOrder.getQuantity());
                results.addAll(this.execute(Order.builder()
                        .orderId(inactiveOrder.getOrderId())
                        .security(security)
                        .side(inactiveOrder.getSide())
                        .quantity(inactiveOrder.getQuantity())
                        .price(inactiveOrder.getPrice())
                        .broker(inactiveOrder.getBroker())
                        .shareholder(inactiveOrder.getShareholder())
                        .entryTime(inactiveOrder.getEntryTime())
                        .status(OrderStatus.ACTIVE)
                        .build()
                ));
            }
        }
        return results;
    }

    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        assert newOrder.getSide() == Side.BUY;
        newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
        trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));

        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreSellOrder(it.previous().getSell());
        }
    }

    private void processMatchResult(MatchResult result, Order order) {
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return;

        if (result.remainder().getQuantity() > 0) {
            if (order.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy((long)order.getPrice() * order.getQuantity());
            }
            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }
        if (!result.trades().isEmpty()) {
            for (Trade trade : result.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
    }

    public LinkedList<MatchResult> execute(Order order) {
        MatchResult mainReqResult = match(order);
        this.processMatchResult(mainReqResult, order);
        LinkedList<MatchResult> results = checkAndActivateStopLimitOrderBook(order.getSecurity());
        results.add(mainReqResult);
        return results;
    }

    public LinkedList<MatchResult> execute(Order order, int minimumExecutionQuantity) {
        assert order.getStatus() == OrderStatus.NEW;
        LinkedList<MatchResult> results = new LinkedList<>();

        MatchResult result = match(order);
        if (minimumExecutionQuantity != 0 && !this.isMinimumExecutionQuantityMet(result, minimumExecutionQuantity)) {
            rollbackTrades(order, result.trades());
            results.add(MatchResult.minimumExecutionQuantityNotMet());
            return results;
        }
        this.processMatchResult(result, order);
        results = checkAndActivateStopLimitOrderBook(order.getSecurity());
        results.add(result);
        return results;
    }

    private boolean isMinimumExecutionQuantityMet(MatchResult result, int minimumExecutionQuantity) {
        return (result.remainder().getQuantity() == 0 ||
                result.trades().stream()
                        .mapToInt(Trade::getQuantity)
                        .sum() >= minimumExecutionQuantity);
    }

}
