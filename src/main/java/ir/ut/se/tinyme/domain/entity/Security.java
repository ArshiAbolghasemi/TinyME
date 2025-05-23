package ir.ut.se.tinyme.domain.entity;

import ir.ut.se.tinyme.domain.service.OrderFactory;
import ir.ut.se.tinyme.messaging.exception.InvalidRequestException;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.request.MatcherState;
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
    private OrderBook stopLimitOrderList = new OrderBook();
    @Builder.Default
    private OrderBook selectedOrdersList = new OrderBook();
    @Setter
    @Builder.Default
    private int lastTradePrice = 0;
    @Setter
    @Builder.Default
    private MatcherState state = MatcherState.CONTINUOUS;
    @Setter
    @Builder.Default
    private AuctionData auctionData = new AuctionData(0,0);

    public LinkedList<MatchResult> newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        LinkedList<MatchResult> results = new LinkedList<>();
        if (enterOrderRq.getSide() == Side.SELL && !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity())){
            results.add(MatchResult.notEnoughPositions(OrderFactory.getInstance().createOrder(enterOrderRq, shareholder, this, broker)));
            return results ;
        }

        if (enterOrderRq.getSide() == Side.BUY && (enterOrderRq.getStopPrice() != 0 || this.state == MatcherState.AUCTION)) {
            if (!broker.hasEnoughCredit((long) enterOrderRq.getPrice() * enterOrderRq.getQuantity())) {
                results.add(MatchResult.notEnoughCredit(OrderFactory.getInstance().createOrder(enterOrderRq, shareholder, this, broker)));
                return results;
            }
            broker.decreaseCreditBy((long) enterOrderRq.getPrice() * enterOrderRq.getQuantity());
        }

            Order order = OrderFactory.getInstance().createOrder(enterOrderRq, shareholder, this, broker);
            if (order instanceof StopLimitOrder) {
                stopLimitOrderList.enqueue(order);
                results.add(MatchResult.noMatchingOccurred());
                return results;
            } else {
                return matcher.execute(order);
            }
    }

    public MatchResult deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
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
        if (this.state == MatcherState.AUCTION) {
            this.setAuctionData(orderBook.calculateTheBestOpeningPrice(this.lastTradePrice));
            return MatchResult.newOpenPriceCalculated(this);
        }
        return null;
    }

    public LinkedList<MatchResult> updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        LinkedList<MatchResult> results = new LinkedList<>();
        Order order;
        order = FindOrder(updateOrderRq);
        ValidateUpdateOrder(updateOrderRq, order);
        if (updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity())){
            results.add(MatchResult.notEnoughPositions(order.snapshotWithRQ(updateOrderRq.getRequestId())));
            return results;
        }

        if (order instanceof StopLimitOrder) {
            return UpdateStopLimitOrder(updateOrderRq, results, order);
        } else {
            return UpdateNormalOrder(updateOrderRq, matcher, results, order);
        }
    }

    private LinkedList<MatchResult> UpdateNormalOrder(EnterOrderRq updateOrderRq, Matcher matcher, LinkedList<MatchResult> results, Order order) {
        if (updateOrderRq.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(order.getValue());
        }
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);
        if (!LosesPriority(originalOrder, updateOrderRq)) {
            if (updateOrderRq.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            results.add(MatchResult.executed(order, List.of()));
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

    private boolean LosesPriority(Order order, EnterOrderRq updateOrderRq) {
        return order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));
    }

    private static LinkedList<MatchResult> UpdateStopLimitOrder(EnterOrderRq updateOrderRq, LinkedList<MatchResult> results, Order order) {
        if (order.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(order.getValue());
        }
        if (updateOrderRq.getSide() == Side.BUY && !order.getBroker().hasEnoughCredit(
                (long) updateOrderRq.getPrice() * updateOrderRq.getQuantity())) {
            order.getBroker().decreaseCreditBy(order.getValue());
            results.add(MatchResult.notEnoughCredit(order.snapshotWithRQ(updateOrderRq.getRequestId())));
            return results;
        }
        order.updateFromRequest(updateOrderRq);
        if (order.getSide() == Side.BUY) {
            order.getBroker().decreaseCreditBy(order.getValue());
        }
        results.add(MatchResult.executed(order, List.of()));
        return results;
    }

    private static void ValidateUpdateOrder(EnterOrderRq updateOrderRq, Order order) throws InvalidRequestException {
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if((order instanceof StopLimitOrder) && order.getSecurity().getState() == MatcherState.AUCTION)
            throw new InvalidRequestException(Message.CANT_UPDATE_STOP_LIMIT_ORDER_ON_AUCTION_MODE);
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        if (order instanceof MEQOrder && updateOrderRq.getMinimumExecutionQuantity() != ((MEQOrder) order).getMinimumExecutionQuantity())
            throw new InvalidRequestException(Message.COULD_NOT_UPDATE_MEQ);
        if (updateOrderRq.getStopPrice() != 0 && !(order instanceof StopLimitOrder)) {
            throw new InvalidRequestException(Message.COULD_NOT_UPDATE_STOP_LIMIT_PRICE_FOR_NON_LIMIT_PRICE_ORDER_OR_NON_ACTIVE_STOPLIMIT_ORDER);
        }
    }

    private Order FindOrder(EnterOrderRq updateOrderRq) {
        Order order;
        order = stopLimitOrderList.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null) {
            order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        }
        return order;
    }

    public void FillSelectedOrderList() {
        if(this.state == MatcherState.AUCTION){
            while(!orderBook.getBuyQueue().isEmpty() &&
                    orderBook.getBuyQueue().getFirst().price >= auctionData.getBestOpeningPrice())
            {
                 Order order = orderBook.getBuyQueue().getFirst();
                 order.getBroker().increaseCreditBy((long)order.getPrice() * order.getQuantity());
                 orderBook.getBuyQueue().removeFirst();
                 selectedOrdersList.enqueue(order);
            }
        } else {
            while (!orderBook.getBuyQueue().isEmpty()) {
                Order order = orderBook.getBuyQueue().getFirst();
                order.getBroker().increaseCreditBy((long)order.getPrice() * order.getQuantity());
                orderBook.getBuyQueue().removeFirst();
                selectedOrdersList.enqueue(order);
            }
        }
    }
}
