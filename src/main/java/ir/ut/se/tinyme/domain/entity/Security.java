package ir.ut.se.tinyme.domain.entity;

import ir.ut.se.tinyme.messaging.exception.InvalidRequestException;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.messaging.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    private OrderBook stopLimitOrderList = new OrderBook();
    @Setter
    @Builder.Default
    private int lastTradePrice = 0;

    public LinkedList<MatchResult> newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        LinkedList<MatchResult> results = new LinkedList<>();
        if (enterOrderRq.getSide() == Side.SELL && !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity())){
            results.add(MatchResult.notEnoughPositions());
            return results ;
        }

        if (enterOrderRq.getSide() == Side.BUY && enterOrderRq.getStopPrice() != 0) {
            if (!broker.hasEnoughCredit((long) enterOrderRq.getPrice() * enterOrderRq.getQuantity())) {
                results.add(MatchResult.notEnoughCredit());
                return results;
            }
            broker.decreaseCreditBy((long) enterOrderRq.getPrice() * enterOrderRq.getQuantity());
        }

        Order order;
        int stopPrice = enterOrderRq.getStopPrice();
        if (stopPrice != 0){
            if ((enterOrderRq.getSide() == Side.SELL && stopPrice >= lastTradePrice) || (enterOrderRq.getSide() == Side.BUY && stopPrice <= lastTradePrice)) {
                if (enterOrderRq.getSide() == Side.BUY) {
                    broker.increaseCreditBy((long) enterOrderRq.getPrice() * enterOrderRq.getQuantity());
                }
                order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), OrderStatus.ACTIVE);
                return matcher.execute(order);
            }else {
                order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), OrderStatus.INACTIVE, stopPrice);
                stopLimitOrderList.enqueue(order);
            }
        }else{
            if (enterOrderRq.getPeakSize() == 0) {
                order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity());
            }else {
                order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(),
                        enterOrderRq.getMinimumExecutionQuantity());
            }
            return matcher.execute(order, enterOrderRq.getMinimumExecutionQuantity());
        }
        results.add(MatchResult.noMatchingOccurred());
        return results;
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());

        if (order == null){
            order = stopLimitOrderList.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
            if (order == null){
                throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
            }
        }
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }

    public LinkedList<MatchResult> updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        LinkedList<MatchResult> results = new LinkedList<>();
        Order order;
        if(updateOrderRq.getStopPrice() != 0) {
            order = stopLimitOrderList.findByOrderId(updateOrderRq.getSide(),updateOrderRq.getOrderId());
            if (order == null) {
                order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
            }
        } else {
            order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        }
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        if (updateOrderRq.getMinimumExecutionQuantity() != order.getMinimumExecutionQuantity()) {
            throw new InvalidRequestException(Message.COULD_NOT_UPDATE_MEQ);
        }
        if (updateOrderRq.getStopPrice() != 0) {
            if (order.getStopPrice() == 0) {
                throw new InvalidRequestException(Message.COULD_NOT_UPDATE_STOP_LIMIT_PRICE_FOR_NON_LIMIT_PRICE_ORDER_OR_NON_ACTIVE_STOPLIMIT_ORDER);
            }
//            if (order.getStatus() != OrderStatus.INACTIVE) {
//                throw new InvalidRequestException(Message.COULD_NOT_UPDATE_STOP_LIMIT_PRICE_FOR_NON_LIMIT_PRICE_ORDER_OR_NON_ACTIVE_STOPLIMIT_ORDER);
//            }
        }

        if (updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity())){
            results.add(MatchResult.notEnoughPositions());
            return results;
        }

        if (order.getStopPrice() != 0 && order.getStatus() == OrderStatus.INACTIVE) {
            if (order.getSide() == Side.BUY) {
                order.getBroker().increaseCreditBy(order.getValue());
            }
            if (updateOrderRq.getSide() == Side.BUY && !order.getBroker().hasEnoughCredit(
                    (long) updateOrderRq.getPrice() * updateOrderRq.getQuantity())) {
                results.add(MatchResult.notEnoughCredit());
                return results;
            }
            order.updateFromRequest(updateOrderRq);
            if (order.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            return results;
        }

        boolean losesPriority = order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));

        if (updateOrderRq.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(order.getValue());
        }
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);
        if (!losesPriority) {
            if (updateOrderRq.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            results.add(MatchResult.executed(null, List.of()));
            return results;
        }

        orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        results = matcher.execute(order);
        if (results.getLast().outcome() != MatchingOutcome.EXECUTED) {
            orderBook.enqueue(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }
        return results;
    }
}
