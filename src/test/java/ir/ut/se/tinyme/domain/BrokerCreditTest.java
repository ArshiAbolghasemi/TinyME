package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@SpringBootTest
@DirtiesContext
public class BrokerCreditTest {
    private Security security;
    private Broker buyerBroker;
    private Broker sellerBroker;
    private Shareholder sellerShareholder;

    private Shareholder buyerShareholder;
    private OrderBook orderBook;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        sellerBroker = Broker.builder().credit(0).build();
        buyerBroker = Broker.builder().credit(100_000_000L).build();
        sellerShareholder = Shareholder.builder().build();
        sellerShareholder.incPosition(security, 100_000);
        buyerShareholder = Shareholder.builder().build();
        buyerShareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        List<Order> orders = Arrays.asList(
                Order.builder()
                        .orderId(1)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(304)
                        .price(15700)
                        .broker(buyerBroker)
                        .shareholder(buyerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(2)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(43)
                        .price(15500)
                        .broker(buyerBroker)
                        .shareholder(buyerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(3)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(445)
                        .price(15450)
                        .broker(buyerBroker)
                        .shareholder(buyerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(4)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(526)
                        .price(15450)
                        .broker(buyerBroker)
                        .shareholder(buyerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(5)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(1000)
                        .price(15400)
                        .broker(buyerBroker)
                        .shareholder(buyerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(6)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(350)
                        .price(15800)
                        .broker(sellerBroker)
                        .shareholder(sellerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(7)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(285)
                        .price(15810)
                        .broker(sellerBroker)
                        .shareholder(sellerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(8)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(800)
                        .price(15810)
                        .broker(sellerBroker)
                        .shareholder(sellerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(9)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(340)
                        .price(15820)
                        .broker(sellerBroker)
                        .shareholder(sellerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(10)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(65)
                        .price(15820)
                        .broker(sellerBroker)
                        .shareholder(sellerShareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build()
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void new_buy_order_matches_with_entire_sell_queue_with_remain_quantity() {
        Order newOrder = Order.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(2000)
                .price(15820)
                .broker(buyerBroker)
                .shareholder(buyerShareholder)
                .entryTime(LocalDateTime.now())
                .status(OrderStatus.NEW)
                .build();
        LinkedList<MatchResult> results =  matcher.execute(newOrder);
        assertThat(results).hasSize(1);
        MatchResult result = results.get(0);
        Order remainder = result.remainder();
        assertThat(result.trades().size()).isEqualTo(5);
        assertThat(remainder.getOrderId()).isEqualTo(11);
        assertThat(remainder.getQuantity()).isEqualTo(160);
        assertThat(remainder.getPrice()).isEqualTo(15820);
        assertThat(remainder.getStatus()).isEqualTo(OrderStatus.QUEUED);

        assertThat(buyerBroker.getCredit()).isEqualTo(68_377_850L);
        assertThat(sellerBroker.getCredit()).isEqualTo(29_090_950L);

        assertThat(security.getOrderBook().getBuyQueue()).contains(remainder);
        assertThat(security.getOrderBook().getSellQueue()).isEmpty();

        assertThat(buyerShareholder.getPositions().get(security)).isEqualTo(101_840);
        assertThat(sellerShareholder.getPositions().get(security)).isEqualTo(98_160);
    }

    @Test
    void new_buy_order_matches_partially_with_seller_orders() {
        Order newOrder = Order.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(1000)
                .price(15810)
                .broker(buyerBroker)
                .shareholder(buyerShareholder)
                .entryTime(LocalDateTime.now())
                .status(OrderStatus.NEW)
                .build();
        LinkedList<MatchResult> results =  matcher.execute(newOrder);
        assertThat(results).hasSize(1);
        MatchResult result = results.get(0);
        LinkedList<Trade> trades = result.trades();
        assertThat(trades.size()).isEqualTo(3);
        assertThat(trades.getLast().getQuantity()).isEqualTo(365);

        Order remainder = result.remainder();
        assertThat(remainder.getQuantity()).isEqualTo(0);
        assertThat(remainder.getPrice()).isEqualTo(15810);
        assertThat(remainder.getStatus()).isEqualTo(OrderStatus.NEW);

        assertThat(buyerBroker.getCredit()).isEqualTo(84_193_500L);
        assertThat(sellerBroker.getCredit()).isEqualTo(15_806_500L);

        assertThat(buyerShareholder.getPositions().get(security)).isEqualTo(101_000);
        assertThat(sellerShareholder.getPositions().get(security)).isEqualTo(99_000);
    }

    @Test
    void new_buy_order_not_match_any_sell_order() {
        Order newOrder = Order.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(1000)
                .price(10_000)
                .broker(buyerBroker)
                .shareholder(buyerShareholder)
                .entryTime(LocalDateTime.now())
                .status(OrderStatus.NEW)
                .build();
        LinkedList<MatchResult> results =  matcher.execute(newOrder);
        assertThat(results).hasSize(1);
        MatchResult result = results.get(0);
        assertThat(result.trades()).isEmpty();
        assertThat(result.remainder()).isEqualTo(newOrder);

        assertThat(buyerBroker.getCredit()).isEqualTo(90_000_000L);
        assertThat(sellerBroker.getCredit()).isEqualTo(0);

        assertThat(buyerShareholder.getPositions().get(security)).isEqualTo(100_000);
        assertThat(sellerShareholder.getPositions().get(security)).isEqualTo(100_000);
    }

    @Test
    void new_buy_order_not_enough_credit() {
        buyerBroker.decreaseCreditBy(90_000_000L);
        Order newOrder = Order.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(2000)
                .price(15810)
                .broker(buyerBroker)
                .shareholder(buyerShareholder)
                .entryTime(LocalDateTime.now())
                .status(OrderStatus.NEW)
                .build();
        LinkedList<MatchResult> results =  matcher.execute(newOrder);
        assertThat(results).hasSize(1);
        MatchResult result = results.get(0);
        assertThat(result.trades()).isEmpty();
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_CREDIT);

        assertThat(buyerBroker.getCredit()).isEqualTo(10_000_000L);
        assertThat(sellerBroker.getCredit()).isEqualTo(0);

        Order firstSellOrder = orderBook.getSellQueue().get(0);
        assertThat(firstSellOrder.getOrderId()).isEqualTo(6);
        assertThat(firstSellOrder.getQuantity()).isEqualTo(350);

        Order secondSellOrder = orderBook.getSellQueue().get(1);
        assertThat(secondSellOrder.getOrderId()).isEqualTo(7);
        assertThat(secondSellOrder.getQuantity()).isEqualTo(285);
    }

    @Test
    void new_buy_order_matches_completely_with_sell_order_ice_berg_in_order_book() {
        IcebergOrder icebergOrderSell = IcebergOrder.builder()
                .orderId(11)
                .security(security)
                .side(Side.SELL)
                .quantity(65)
                .price(15820)
                .broker(sellerBroker)
                .shareholder(sellerShareholder)
                .peakSize(50)
                .displayedQuantity(Math.min(50, 65))
                .build();

        IcebergOrder icebergOrder = IcebergOrder.builder()
                .orderId(11)
                .security(security)
                .side(Side.SELL)
                .quantity(65)
                .price(15820)
                .broker(sellerBroker)
                .shareholder(sellerShareholder)
                .peakSize(50)
                .displayedQuantity(Math.min(65, 50))
                .build();

        orderBook.enqueue(icebergOrderSell);

        Order newBuyOrder = Order.builder()
                .orderId(12)
                .security(security)
                .side(Side.BUY)
                .quantity(2000)
                .price(15820)
                .broker(buyerBroker)
                .shareholder(buyerShareholder)
                .build();

        LinkedList<MatchResult> results =  matcher.execute(newBuyOrder);
        assertThat(results).hasSize(1);
        MatchResult result = results.get(0);

        Order remainder = result.remainder();
        assertThat(result.trades().size()).isEqualTo(7);
        assertThat(remainder.getQuantity()).isEqualTo(95);
        assertThat(remainder.getPrice()).isEqualTo(15820);
        assertThat(remainder.getStatus()).isEqualTo(OrderStatus.QUEUED);

        assertThat(buyerBroker.getCredit()).isEqualTo(68_377_850L);
        assertThat(sellerBroker.getCredit()).isEqualTo(30_119_250L);
    }

    @Test
    void new_order_sell_match_complete_buy_orders() {
        Order newBuyOrder = Order.builder()
                .orderId(11)
                .security(security)
                .side(Side.SELL)
                .quantity(2500)
                .price(15000)
                .broker(sellerBroker)
                .shareholder(sellerShareholder)
                .build();

        LinkedList<MatchResult> results =  matcher.execute(newBuyOrder);
        assertThat(results).hasSize(1);
        MatchResult result = results.get(0);
        Order remainder = result.remainder();
        assertThat(result.trades().size()).isEqualTo(5);
        assertThat(remainder.getQuantity()).isEqualTo(182);
        assertThat(remainder.getPrice()).isEqualTo(15000);
        assertThat(remainder.getStatus()).isEqualTo(OrderStatus.QUEUED);

        assertThat(sellerBroker.getCredit()).isEqualTo(35_841_250L);

        assertThat(buyerShareholder.getPositions().get(security)).isEqualTo(102_318);
        assertThat(sellerShareholder.getPositions().get(security)).isEqualTo(97_682);
    }

    @Test
    void new_order_sell_match_partially_buy_order() {
        Order newBuyOrder = Order.builder()
                .orderId(11)
                .security(security)
                .side(Side.SELL)
                .quantity(2000)
                .price(15000)
                .broker(sellerBroker)
                .shareholder(sellerShareholder)
                .build();

        LinkedList<MatchResult> results =  matcher.execute(newBuyOrder);
        assertThat(results).hasSize(1);
        MatchResult result = results.get(0);
        Order remainder = result.remainder();
        assertThat(result.trades().size()).isEqualTo(5);
        assertThat(remainder.getQuantity()).isEqualTo(0);
        assertThat(remainder.getPrice()).isEqualTo(15000);

        assertThat(sellerBroker.getCredit()).isEqualTo(30_944_050);

        assertThat(buyerShareholder.getPositions().get(security)).isEqualTo(102_000);
        assertThat(sellerShareholder.getPositions().get(security)).isEqualTo(98_000);
    }

    @Test
    void delete_order_buy() {
        Order deleteOrder = Order.builder()
                .orderId(5)
                .security(security)
                .side(Side.BUY)
                .quantity(1000)
                .price(15400)
                .broker(buyerBroker)
                .shareholder(buyerShareholder)
                .build();
        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, deleteOrder.getSecurity().getIsin(),
                deleteOrder.getSide(), deleteOrder.getOrderId());
        assertThatNoException().isThrownBy(() -> deleteOrder.getSecurity().deleteOrder(deleteOrderRq));
        assertThat(buyerBroker.getCredit()).isEqualTo(115_400_000L);
    }

    @Test
    void update_order_buy_quantity_without_lose_priority() {
        Order toBeUpdateOrder = Order.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(304)
                .price(15700)
                .broker(buyerBroker)
                .shareholder(buyerShareholder)
                .build();
        orderBook.enqueue(toBeUpdateOrder);
        EnterOrderRq enterOrderRq = EnterOrderRq.createUpdateOrderRq(
                1,
                toBeUpdateOrder.getSecurity().getIsin(),
                toBeUpdateOrder.getOrderId(),
                LocalDateTime.now(),
                toBeUpdateOrder.getSide(),
                toBeUpdateOrder.getQuantity() - 100,
                toBeUpdateOrder.getPrice(),
                toBeUpdateOrder.getBroker().getBrokerId(),
                toBeUpdateOrder.getShareholder().getShareholderId(),
                0,
                0
        );

        assertThatNoException().isThrownBy(() -> security.updateOrder(enterOrderRq, matcher));
        assertThat(buyerBroker.getCredit()).isEqualTo(101_570_000L);
    }

    @Test
    void update_order_buy_increase_quantity_match_completely_sell_orders() {
        Order toBeUpdateOrder = Order.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(2000)
                .price(15820)
                .broker(buyerBroker)
                .shareholder(buyerShareholder)
                .build();
        orderBook.enqueue(toBeUpdateOrder);

        EnterOrderRq enterOrderRq = EnterOrderRq.createUpdateOrderRq(1, toBeUpdateOrder.getSecurity().getIsin(),
                toBeUpdateOrder.getOrderId(), LocalDateTime.now(), toBeUpdateOrder.getSide(), 2100,
                toBeUpdateOrder.getPrice(), toBeUpdateOrder.getBroker().getBrokerId(),
                toBeUpdateOrder.getShareholder().getShareholderId(), 0, 0);

        assertThatNoException().isThrownBy(() -> security.updateOrder(enterOrderRq, matcher));

        assertThat(buyerBroker.getCredit()).isEqualTo(98_435_850L);
        assertThat(sellerBroker.getCredit()).isEqualTo(29_090_950L);
    }

    @Test
    void update_order_buy_increase_quantity_not_enough_credit() {
        buyerBroker.decreaseCreditBy(99_000_000L);
        Order toBeUpdateOrder = Order.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(1000)
                .price(15810)
                .broker(buyerBroker)
                .shareholder(buyerShareholder)
                .build();

        orderBook.enqueue(toBeUpdateOrder);
        EnterOrderRq enterOrderRq = EnterOrderRq.createUpdateOrderRq(1, toBeUpdateOrder.getSecurity().getIsin(),
                toBeUpdateOrder.getOrderId(), LocalDateTime.now(), toBeUpdateOrder.getSide(), 1100,
                toBeUpdateOrder.getPrice(), toBeUpdateOrder.getBroker().getBrokerId(),
                toBeUpdateOrder.getShareholder().getShareholderId(), 0, 0);

        assertThatNoException().isThrownBy(() -> security.updateOrder(enterOrderRq, matcher));

        assertThat(buyerBroker.getCredit()).isEqualTo(1_000_000L);
        assertThat(sellerBroker.getCredit()).isEqualTo(0);
    }

}
