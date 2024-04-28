package ir.ut.se.tinyme.domain.entity;

import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder()
@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public class StopLimitOrder extends Order {
    protected int stopPrice;

    public boolean queuesBefore(Order order) {
        if (status != OrderStatus.INACTIVE) {
            return super.queuesBefore(order);
        }

        assert order instanceof StopLimitOrder;
        StopLimitOrder stopLimitOrder = (StopLimitOrder) order;

        if (stopPrice == stopLimitOrder.getStopPrice()) {
            return entryTime.isBefore(order.getEntryTime());
        }

        return order.getSide() == Side.BUY ?
                stopPrice < ((StopLimitOrder) order).getStopPrice() :
                stopPrice > ((StopLimitOrder) order).getStopPrice();
    }

    public void queue() {
        if (status == OrderStatus.INACTIVE) return;
        super.queue();
    }

    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        super.updateFromRequest(updateOrderRq);
        stopPrice = updateOrderRq.getStopPrice();
    }

    public boolean canBeActivate(int lastTradePrice) {
        return ((side == Side.SELL && stopPrice >= lastTradePrice) ||
                side == Side.BUY && stopPrice <= lastTradePrice);
    }

}
