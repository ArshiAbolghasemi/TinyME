package ir.ut.se.tinyme.domain.entity;

import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
public class IcebergOrder extends Order {
    protected int peakSize;
    protected int displayedQuantity;

    @Override
    public Order snapshot() {
        return builder()
                .orderId(orderId)
                .security(security)
                .side(side)
                .quantity(quantity)
                .price(price)
                .broker(broker)
                .shareholder(shareholder)
                .entryTime(entryTime)
                .peakSize(peakSize)
                .displayedQuantity(Math.min(quantity, peakSize))
                .minimumExecutionQuantity(minimumExecutionQuantity)
                .status(OrderStatus.SNAPSHOT)
                .build();
    }

    @Override
    public Order snapshotWithQuantity(int newQuantity) {
        return builder()
                .orderId(orderId)
                .security(security)
                .side(side)
                .quantity(newQuantity)
                .price(price)
                .broker(broker)
                .shareholder(shareholder)
                .entryTime(entryTime)
                .peakSize(peakSize)
                .displayedQuantity(Math.min(newQuantity, peakSize))
                .minimumExecutionQuantity(minimumExecutionQuantity)
                .status(OrderStatus.SNAPSHOT)
                .build();
    }

    @Override
    public int getQuantity() {
        if (status == OrderStatus.NEW)
            return super.getQuantity();
        return displayedQuantity;
    }

    @Override
    public void decreaseQuantity(int amount) {
        if (status == OrderStatus.NEW) {
            super.decreaseQuantity(amount);
            return;
        }
        if (amount > displayedQuantity)
            throw new IllegalArgumentException();
        quantity -= amount;
        displayedQuantity -= amount;
    }

    public void replenish() {
        displayedQuantity = Math.min(quantity, peakSize);
    }

    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        super.updateFromRequest(updateOrderRq);
        if (peakSize < updateOrderRq.getPeakSize()) {
            displayedQuantity = Math.min(quantity, updateOrderRq.getPeakSize());
        }
        peakSize = updateOrderRq.getPeakSize();
    }
}
