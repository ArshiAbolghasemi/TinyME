package ir.ut.se.tinyme.domain.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
public class MEQOrder extends Order {
    @Builder.Default
    protected int minimumExecutionQuantity = 0;

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
                .status(OrderStatus.SNAPSHOT)
                .minimumExecutionQuantity(minimumExecutionQuantity)
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
                .status(OrderStatus.SNAPSHOT)
                .minimumExecutionQuantity(minimumExecutionQuantity)
                .build();
    }
}
