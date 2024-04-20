package ir.ut.se.tinyme.domain.entity;

import ir.ut.se.tinyme.messaging.exception.InvalidRequestException;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.messaging.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
    private List<Order> stopLimitOrderList = new LinkedList<>();
    @Setter
    @Builder.Default
    private int lastTradePrice = 0;

    public LinkedList<MatchResult> newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity()))
            return (LinkedList<MatchResult>) List.of(MatchResult.notEnoughPositions());

        Order order;
        int stopPrice = enterOrderRq.getStopPrice();
        if (stopPrice != 0){
            if ((enterOrderRq.getSide() == Side.SELL && stopPrice > lastTradePrice) || (enterOrderRq.getSide() == Side.BUY && stopPrice < lastTradePrice)) {
                order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), OrderStatus.ACTIVE);
                return matcher.execute(order);
            }else {
                order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                        enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                        enterOrderRq.getEntryTime(), OrderStatus.INACTIVE, stopPrice);
                stopLimitOrderList.add(order);
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
        return (LinkedList<MatchResult>) List.of(MatchResult.noMatchingOccurred());
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }

    public LinkedList<MatchResult> updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        if (updateOrderRq.getMinimumExecutionQuantity() != order.getMinimumExecutionQuantity()) {
            throw new InvalidRequestException(Message.COULD_NOT_UPDATE_MEQ);
        }

        if (updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity()))
            return (LinkedList<MatchResult>) List.of(MatchResult.notEnoughPositions());

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
            return (LinkedList<MatchResult>) List.of(MatchResult.executed(null, List.of()));
        }

        orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        LinkedList<MatchResult> results = matcher.execute(order);
        if (results.getLast().outcome() != MatchingOutcome.EXECUTED) {
            orderBook.enqueue(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }
        return results;
    }
}
