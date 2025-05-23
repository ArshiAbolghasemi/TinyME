package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.config.MockedJMSTestConfig;
import ir.ut.se.tinyme.messaging.exception.InvalidRequestException;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
class SecurityTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private List<Order> orders;
    @Autowired
    Matcher matcher;
    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker = Broker.builder().brokerId(0).credit(1_000_000L).build();
        shareholder = Shareholder.builder().shareholderId(0).build();
        shareholder.incPosition(security, 100_000);
        orders = Arrays.asList(
                Order.builder()
                        .orderId(1)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(304)
                        .price(15700)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(2)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(43)
                        .price(15500)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(3)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(445)
                        .price(15450)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(4)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(526)
                        .price(15450)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(5)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(1000)
                        .price(15400)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(6)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(350)
                        .price(15800)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(7)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(285)
                        .price(15810)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(8)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(800)
                        .price(15810)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(9)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(340)
                        .price(15820)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(10)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(65)
                        .price(15820)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build()
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
    }

    @Test
    void reducing_quantity_does_not_change_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3,
                LocalDateTime.now(), Side.BUY, 440, 15450, 0, 0, 0,
                0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(2).getQuantity()).isEqualTo(440);
        assertThat(security.getOrderBook().getBuyQueue().get(2).getOrderId()).isEqualTo(3);
    }

    @Test
    void increasing_quantity_changes_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3,
                LocalDateTime.now(), Side.BUY, 450, 15450, 0, 0,
                0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(450);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(3);
    }

    @Test
    void changing_price_changes_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1,
                LocalDateTime.now(), Side.BUY, 300, 15450, 0, 0, 0,
                0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(300);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getPrice()).isEqualTo(15450);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getOrderId()).isEqualTo(2);
    }
    @Test
    void changing_price_causes_trades_to_happen() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 6,
                LocalDateTime.now(), Side.SELL, 350, 15700, 0, 0, 0,
                0);
        assertThatNoException().isThrownBy(() ->
                assertThat(security.updateOrder(updateOrderRq, matcher).getLast().trades()).isNotEmpty()
        );
    }

    @Test
    void updating_non_existing_order_fails() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 6,
                LocalDateTime.now(), Side.BUY, 350, 15700, 0, 0, 0,
                0);
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
    }

    @Test
    void delete_order_works() {
        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, security.getIsin(), Side.SELL, 6);
        assertThatNoException().isThrownBy(() -> security.deleteOrder(deleteOrderRq));
        assertThat(security.getOrderBook().getBuyQueue()).isEqualTo(orders.subList(0, 5));
        assertThat(security.getOrderBook().getSellQueue()).isEqualTo(orders.subList(6, 10));
    }

    @Test
    void deleting_non_existing_order_fails() {
        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, security.getIsin(), Side.SELL, 1);
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.deleteOrder(deleteOrderRq));
    }

    @Test
    void increasing_iceberg_peak_size_changes_priority() {
        security = Security.builder().build();
        broker = Broker.builder().credit(1_000_000L).build();
        orders = Arrays.asList(
                Order.builder()
                        .orderId(1)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(304)
                        .price(15700)
                        .broker(broker)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(2)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(43)
                        .price(15500)
                        .broker(broker)
                        .shareholder(shareholder)
                        .build(),
                IcebergOrder.builder()
                        .orderId(3)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(445)
                        .price(15450)
                        .broker(broker)
                        .shareholder(shareholder)
                        .peakSize(100)
                        .displayedQuantity(Math.min(100, 445))
                        .build(),
                Order.builder()
                        .orderId(4)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(526)
                        .price(15450)
                        .broker(broker)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(5)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(1000)
                        .price(15400)
                        .broker(broker)
                        .shareholder(shareholder)
                        .build()
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3,
                LocalDateTime.now(), Side.BUY, 445, 15450, 0, 0, 150,
                0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(150);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(3);
    }

    @Test
    void decreasing_iceberg_quantity_to_amount_larger_than_peak_size_does_not_changes_priority() {
        security = Security.builder().build();
        broker = Broker.builder().build();
        orders = Arrays.asList(
                Order.builder()
                        .orderId(1)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(304)
                        .price(15700)
                        .broker(broker)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(2)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(43)
                        .price(15500)
                        .broker(broker)
                        .shareholder(shareholder)
                        .build(),
                IcebergOrder.builder()
                        .orderId(3)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(445)
                        .price(15450)
                        .broker(broker)
                        .shareholder(shareholder)
                        .peakSize(100)
                        .displayedQuantity(Math.min(100, 445))
                        .build(),
                Order.builder()
                        .orderId(4)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(526)
                        .price(15450)
                        .broker(broker)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(5)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(1000)
                        .price(15400)
                        .broker(broker)
                        .shareholder(shareholder)
                        .build()
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3,
                LocalDateTime.now(), Side.BUY, 300, 15450, 0, 0, 100,
                0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, matcher));
        assertThat(security.getOrderBook().getBuyQueue().get(2).getOrderId()).isEqualTo(3);
    }

}