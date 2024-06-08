package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.messaging.request.MatcherState;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.ListIterator;

@Service
public class Matcher {
    public MatchResult match(Order newOrder) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();
        Security security = newOrder.getSecurity();

        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;

            int tradePrice = this.calculateTradePrice(matchingOrder, security);

            Trade trade = new Trade(newOrder.getSecurity(), tradePrice, Math.min(newOrder.getQuantity(), matchingOrder.getQuantity()), newOrder, matchingOrder);
            if (newOrder.getSide() == Side.BUY) {
                if (trade.buyerHasEnoughCredit())
                    trade.decreaseBuyersCredit();
                else {
                    rollbackTrades(newOrder, trades);
                    return MatchResult.notEnoughCredit(newOrder);
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

        if (this.shouldRollbackTradesAfterMatch(newOrder)) {
            rollbackTrades(newOrder, trades);
            return MatchResult.notEnoughCredit(newOrder);
        }

        if(!trades.isEmpty()){
            newOrder.getSecurity().setLastTradePrice(trades.getLast().getPrice());
        }
        if (newOrder.getStatus() == OrderStatus.ACTIVE){
            return MatchResult.stopLimitOrderActivated (newOrder, trades);
        }
        return MatchResult.executed(newOrder, trades);
    }

    private boolean shouldRollbackTradesAfterMatch(Order newOrder)
    {
        return (newOrder.getQuantity() > 0 && newOrder.getSide() == Side.BUY &&
            !newOrder.getBroker().hasEnoughCredit((long)newOrder.getPrice() * newOrder.getQuantity()));
    }

    private int calculateTradePrice(Order matchingOrder, Security security) {
       if (security.getState() == MatcherState.CONTINUOUS)
         return matchingOrder.getPrice();

       return security.getAuctionData().getBestOpeningPrice();
    }

    private LinkedList<MatchResult> checkAndActivateStopLimitOrderBook(Security security){
        LinkedList<MatchResult> results = new LinkedList<>();

        for (Order inactiveOrder : security.getStopLimitOrderList().getSellQueue()){
            StopLimitOrder stopLimitOrder = (StopLimitOrder) inactiveOrder;
            if (!stopLimitOrder.canBeActivate(security.getLastTradePrice())) continue;

            security.getStopLimitOrderList().getSellQueue().remove(inactiveOrder);
            activateBasedOnMode(security, results, stopLimitOrder);
        }
        for (Order inactiveOrder : security.getStopLimitOrderList().getBuyQueue()){
            StopLimitOrder stopLimitOrder = (StopLimitOrder) inactiveOrder;
            if (!stopLimitOrder.canBeActivate(security.getLastTradePrice())) continue;

            security.getStopLimitOrderList().getBuyQueue().remove(inactiveOrder);
            inactiveOrder.getBroker().increaseCreditBy(
                    (long)inactiveOrder.getPrice() * inactiveOrder.getQuantity());
            activateBasedOnMode(security, results, stopLimitOrder);
        }
        return results;
    }

    private void activateBasedOnMode(Security security, LinkedList<MatchResult> results, StopLimitOrder stopLimitOrder) {
        if (security.getState() == MatcherState.CONTINUOUS){
            Order activatedOrder = OrderFactory.getInstance().activateStopLimitOrder(stopLimitOrder);
            results.addAll(this.execute(activatedOrder));
        }else {
            Order activatedOrder = OrderFactory.getInstance().clone(stopLimitOrder);
            if (activatedOrder.getSide() == Side.BUY)
                activatedOrder.getBroker().decreaseCreditBy((long)activatedOrder.getPrice()
                    * activatedOrder.getQuantity());
            results.add(MatchResult.stopLimitOrderActivated(activatedOrder, new LinkedList<>()));
            results.add(enqueueAndSetPriceOnAuctionMode(activatedOrder));
        }
    }

    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        if(newOrder.getSide() == Side.BUY) {
            newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
            trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));

            ListIterator<Trade> it = trades.listIterator(trades.size());
            while (it.hasPrevious()) {
                newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getSell());
            }
        }else{
            ListIterator<Trade> it = trades.listIterator(trades.size());
            while (it.hasPrevious()) {
                newOrder.getSecurity().getOrderBook().restoreOrder(it.previous().getBuy());
            }
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

    private MatchResult enqueueAndSetPriceOnAuctionMode(Order order){
        order.getSecurity().getOrderBook().enqueue(order);
        order.getSecurity().setAuctionData( order.getSecurity().getOrderBook().
                calculateTheBestOpeningPrice(order.getSecurity().getLastTradePrice()));
        return MatchResult.newOpenPriceCalculated(order.getSecurity());
    }

    public LinkedList<MatchResult> execute(Order order) {
        LinkedList<MatchResult> results = new LinkedList<>();
        if (order.getSecurity().getState() == MatcherState.CONTINUOUS) {
            MatchResult mainReqResult = match(order);
            this.processMatchResult(mainReqResult, order);
            results = checkAndActivateStopLimitOrderBook(order.getSecurity());
            results.add(mainReqResult);
        }else {
            results.add(enqueueAndSetPriceOnAuctionMode(order));
        }
        return results;
    }

    public LinkedList<MatchResult> execute(Order order, int minimumExecutionQuantity) {
        assert order.getStatus() == OrderStatus.NEW;
        LinkedList<MatchResult> results = new LinkedList<>();

        MatchResult result = match(order);
        if (minimumExecutionQuantity != 0 && !this.isMinimumExecutionQuantityMet(result, minimumExecutionQuantity)) {
            rollbackTrades(order, result.trades());
            results.add(MatchResult.minimumExecutionQuantityNotMet(order));
            return results;
        }
        this.processMatchResult(result, order);
        results = checkAndActivateStopLimitOrderBook(order.getSecurity());
        results.add(result);
        return results;
    }

    public LinkedList<MatchResult> matchOrderBook(Security security){
        OrderBook selectedorderBook = security.getSelectedOrdersList();
        LinkedList<MatchResult> matchResults = new LinkedList<>();
        while (!selectedorderBook.getBuyQueue().isEmpty()){
            Order order = selectedorderBook.getBuyQueue().getFirst();
            selectedorderBook.removeFirst(Side.BUY);
            MatchResult result = this.match(order);
            processMatchResult(result, order);
            matchResults.add(result);
        }
        matchResults.addAll(checkAndActivateStopLimitOrderBook(security));
        return matchResults;
    }

    private boolean isMinimumExecutionQuantityMet(MatchResult result, int minimumExecutionQuantity) {
        return (result.remainder().getQuantity() == 0 ||
                result.trades().stream()
                        .mapToInt(Trade::getQuantity)
                        .sum() >= minimumExecutionQuantity);
    }

}
