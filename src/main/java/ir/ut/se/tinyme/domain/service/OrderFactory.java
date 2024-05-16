package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;

public class OrderFactory {

    private static OrderFactory instance;

    private OrderFactory() {}

    public static OrderFactory getInstance() {
        if (instance == null) {
            instance = new OrderFactory();
        }
        return instance;
    }

    public Order createOrder(EnterOrderRq enterOrderRq, Shareholder shareholder, Security security, Broker broker) {
        if (enterOrderRq.getStopPrice() != 0) {
            StopLimitOrder order = StopLimitOrder.builder()
                    .orderId(enterOrderRq.getOrderId())
                    .security(security)
                    .side(enterOrderRq.getSide())
                    .quantity(enterOrderRq.getQuantity())
                    .broker(broker)
                    .price(enterOrderRq.getPrice())
                    .shareholder(shareholder)
                    .entryTime(enterOrderRq.getEntryTime())
                    .stopPrice(enterOrderRq.getStopPrice())
                    .status(OrderStatus.INACTIVE)
                    .rqId(enterOrderRq.getRequestId())
                    .build();
            return order.canBeActivate(security.getLastTradePrice()) ? activateStopLimitOrder(order) : order;
        } else if (enterOrderRq.getPeakSize() != 0) {
            return IcebergOrder.builder()
                    .peakSize(enterOrderRq.getPeakSize())
                    .displayedQuantity(Math.min(enterOrderRq.getQuantity(), enterOrderRq.getPeakSize()))
                    .orderId(enterOrderRq.getOrderId())
                    .security(security)
                    .side(enterOrderRq.getSide())
                    .quantity(enterOrderRq.getQuantity())
                    .price(enterOrderRq.getPrice())
                    .broker(broker)
                    .shareholder(shareholder)
                    .entryTime(enterOrderRq.getEntryTime())
                    .status(OrderStatus.NEW)
                    .minimumExecutionQuantity(enterOrderRq.getMinimumExecutionQuantity())
                    .rqId(enterOrderRq.getRequestId())
                    .build();
        } else {
            return Order.builder()
                    .orderId(enterOrderRq.getOrderId())
                    .security(security)
                    .side(enterOrderRq.getSide())
                    .quantity(enterOrderRq.getQuantity())
                    .price(enterOrderRq.getPrice())
                    .broker(broker)
                    .shareholder(shareholder)
                    .entryTime(enterOrderRq.getEntryTime())
                    .minimumExecutionQuantity(enterOrderRq.getMinimumExecutionQuantity())
                    .status(OrderStatus.NEW)
                    .rqId(enterOrderRq.getRequestId())
                    .build();
        }
    }

    public Order activateStopLimitOrder(StopLimitOrder stopLimitOrder) {
        return Order.builder()
                .orderId(stopLimitOrder.getOrderId())
                .security(stopLimitOrder.getSecurity())
                .side(stopLimitOrder.getSide())
                .quantity(stopLimitOrder.getQuantity())
                .broker(stopLimitOrder.getBroker())
                .price(stopLimitOrder.getPrice())
                .shareholder(stopLimitOrder.getShareholder())
                .entryTime(stopLimitOrder.getEntryTime())
                .status(OrderStatus.ACTIVE)
                .rqId(stopLimitOrder.getRqId())
                .build();
    }
}
