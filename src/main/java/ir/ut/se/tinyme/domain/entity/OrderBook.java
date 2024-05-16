package ir.ut.se.tinyme.domain.entity;

import lombok.Getter;

import java.util.*;

import static org.apache.commons.lang3.math.NumberUtils.min;

@Getter
public class OrderBook {
    private final LinkedList<Order> buyQueue;
    private final LinkedList<Order> sellQueue;

    public OrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
    }

    public void enqueue(Order order) {
        List<Order> queue = getQueue(order.getSide());
        ListIterator<Order> it = queue.listIterator();
        while (it.hasNext()) {
            if (order.queuesBefore(it.next())) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
    }

    private LinkedList<Order> getQueue(Side side) {
        return side == Side.BUY ? buyQueue : sellQueue;
    }

    public Order findByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        for (Order order : queue) {
            if (order.getOrderId() == orderId)
                return order;
        }
        return null;
    }

    public boolean removeByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        var it = queue.listIterator();
        while (it.hasNext()) {
            if (it.next().getOrderId() == orderId) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    private List<Integer> calculateTheOpeningPriceBoundary(){
        List<Integer> openingPrices = new LinkedList<>();
        for (Order order : sellQueue) {
            openingPrices.add(order.getPrice());
        }
        for (Order order : buyQueue) {
            openingPrices.add(order.getPrice());
        }
        Collections.sort(openingPrices);
        return openingPrices;
    }

    public int calculateTheBestOpeningPrice(){
        List<Integer> openingPrices = calculateTheOpeningPriceBoundary();
        int bestPrice = 0;
        int maxQuantity = 0;
        for (Integer price : openingPrices) {
            int buyQuantity = 0, sellQuantity = 0;
            for (Order order : buyQueue) {
                if (order.getPrice() >= price)
                    buyQuantity+=order.getQuantity();
            }
            for (Order order : sellQueue) {
                if (order.getPrice() <= price)
                    sellQuantity+=order.getQuantity();
            }
            if (min(buyQuantity,sellQuantity) > maxQuantity){
                maxQuantity = min(buyQuantity,sellQuantity);
                bestPrice = price;
            }
        }
        return bestPrice;
    }

    public Order matchWithFirst(Order newOrder) {
        var queue = getQueue(newOrder.getSide().opposite());
        if (newOrder.matches(queue.getFirst()))
            return queue.getFirst();
        else
            return null;
    }

    public void putBack(Order order) {
        LinkedList<Order> queue = getQueue(order.getSide());
        order.queue();
        queue.addFirst(order);
    }

    public void restoreSellOrder(Order sellOrder) {
        removeByOrderId(Side.SELL, sellOrder.getOrderId());
        putBack(sellOrder);
    }

    public boolean hasOrderOfType(Side side) {
        return !getQueue(side).isEmpty();
    }

    public void removeFirst(Side side) {
        getQueue(side).removeFirst();
    }

    public int totalSellQuantityByShareholder(Shareholder shareholder) {
        return sellQueue.stream()
                .filter(order -> order.getShareholder().equals(shareholder))
                .mapToInt(Order::getTotalQuantity)
                .sum();
    }
}
