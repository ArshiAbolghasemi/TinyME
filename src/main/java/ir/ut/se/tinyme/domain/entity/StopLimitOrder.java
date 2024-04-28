package ir.ut.se.tinyme.domain.entity;

import ir.ut.se.tinyme.lib.dto.CreateStopLimitOrderDTO;
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
    protected int stopPriceV2;

    public boolean queuesBefore(Order order) {
        if (status != OrderStatus.ACTIVE) {
            return super.queuesBefore(order);
        }

        assert order instanceof StopLimitOrder;
        StopLimitOrder stopLimitOrder = (StopLimitOrder) order;

        if (stopPriceV2 == stopLimitOrder.getStopPriceV2()) {
            return entryTime.isBefore(order.getEntryTime());
        }

        return order.getSide() == Side.BUY ?
                stopPriceV2 > ((StopLimitOrder) order).getStopPriceV2() :
                stopPriceV2 < ((StopLimitOrder) order).getStopPriceV2();
    }

    public void queue() {
        if (status == OrderStatus.INACTIVE) return;
        super.queue();
    }

    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        super.updateFromRequest(updateOrderRq);
        stopPriceV2 = updateOrderRq.getStopPrice();
    }

    public boolean canBeActivate(int lastTradePrice) {
        return ((side == Side.SELL && stopPriceV2 >= lastTradePrice) ||
                side == Side.BUY && stopPriceV2 <= lastTradePrice);
    }

}
