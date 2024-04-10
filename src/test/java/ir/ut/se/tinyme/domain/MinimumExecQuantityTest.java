package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.domain.service.OrderHandler;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.event.OrderRejectedEvent;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.messaging.request.OrderEntryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DirtiesContext
public class MinimumExecQuantityTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;
    @Autowired
    private OrderHandler orderHandler;
    @Autowired
    EventPublisher eventPublisher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker = Broker.builder().credit(100_000_000L).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, broker, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));

    }

    @Test
    void new_order_request_where_MEQ_is_out_of_range(){
        EnterOrderRq rq = EnterOrderRq.createNewOrderRq(1, security.getIsin(), 200, LocalDateTime.now(),
                Side.SELL, 300, 15450, broker.getBrokerId(), 0, 0, 500);
        orderHandler.handleEnterOrder(rq);
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(-1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.INVALID_MINIMUM_EXECUTION_QUANTITY_RANGE
        );
    }

    @Test
    void order_update_where_MEQ_shouldnt_change(){
        Order newOrder = new Order(11, security, Side.BUY, 304, 15700, broker, shareholder,
                LocalDateTime.now() ,100);

        newOrder.updateFromRequest(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 11, LocalDateTime.now(),
                Side.BUY, 400, 15450, broker.getBrokerId(), 0, 0, 200));

        assertThat(newOrder.getMinimumExecutionQuantity()).isEqualTo(100);
    }

    @Test
    void order_update_where_update_request_dosnt_change_MEQ(){
        Order newOrder = new Order(11, security, Side.BUY, 304, 15700, broker, shareholder,
                LocalDateTime.now() ,100);

        newOrder.updateFromRequest(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 11, LocalDateTime.now(),
                Side.BUY, 400, 15450, broker.getBrokerId(), 0, 0, 100));

        assertThat(newOrder.getMinimumExecutionQuantity()).isEqualTo(100);
    }
}
