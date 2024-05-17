package ir.ut.se.tinyme.domain.entity;

import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.messaging.request.MatcherState;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@SuperBuilder
@EqualsAndHashCode
@ToString
@Getter
public class Order {
    protected long orderId;
    protected Security security;
    protected Side side;
    protected int quantity;
    protected int price;
    protected Broker broker;
    protected Shareholder shareholder;
    @Builder.Default
    protected LocalDateTime entryTime = LocalDateTime.now();
    @Builder.Default
    protected OrderStatus status = OrderStatus.NEW;
    @Builder.Default
    protected int minimumExecutionQuantity = 0;
    protected long rqId;

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
                .rqId(rqId)
                .build();
    }

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
                .rqId(rqId)
                .build();
    }

    public boolean matches(Order other) {
        if (this.getSecurity().getState() == MatcherState.CONTINUOUS){
            if (side == Side.BUY)
                return price >= other.price;
            else
                return price <= other.price;
        }else {
            int openingPrice = this.getSecurity().getAuctionData().getBestOpeningPrice();
            if (side == Side.BUY)
                return price >= openingPrice && other.price <= openingPrice;
            else
                return other.price >= openingPrice && price <= openingPrice;
        }
    }

    public void decreaseQuantity(int amount) {
        if (amount > quantity)
            throw new IllegalArgumentException();
        quantity -= amount;
    }

    public void makeQuantityZero() {
        quantity = 0;
    }

    public boolean queuesBefore(Order order) {
        if (order.getSide() == Side.BUY) {
            return price > order.getPrice();
        } else {
            return price < order.getPrice();
        }
    }

    public void queue() {
        status = OrderStatus.QUEUED;
    }

    public boolean isQuantityIncreased(int newQuantity) {
        return newQuantity > quantity;
    }

    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        quantity = updateOrderRq.getQuantity();
        price = updateOrderRq.getPrice();
        rqId = updateOrderRq.getRequestId();
    }

    public long getValue() {
        return (long)price * quantity;
    }

    public int getTotalQuantity() { return quantity; }
}
