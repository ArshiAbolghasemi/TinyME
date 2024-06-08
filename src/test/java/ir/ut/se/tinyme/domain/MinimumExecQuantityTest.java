package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.domain.service.OrderHandler;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.event.Event;
import ir.ut.se.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ut.se.tinyme.messaging.event.OrderRejectedEvent;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.repository.BrokerRepository;
import ir.ut.se.tinyme.repository.SecurityRepository;
import ir.ut.se.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

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
    private SecurityRepository securityRepository;
    @Autowired
    private BrokerRepository brokerRepository;
    @Autowired
    private ShareholderRepository shareholderRepository;
    private EventPublisher mockEventPublisher;
    private OrderHandler mockOrderHandler;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);
        broker = Broker.builder().brokerId(1).credit(100_000_000L).build();
        brokerRepository.addBroker(broker);
        shareholder = Shareholder.builder().shareholderId(1).build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);
        orderBook = security.getOrderBook();
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
        orders.forEach(order -> orderBook.enqueue(order));
        mockEventPublisher = mock(EventPublisher.class, withSettings().verboseLogging());
        mockOrderHandler = new OrderHandler(securityRepository, brokerRepository, shareholderRepository,
                mockEventPublisher, matcher);
    }

    @Test
    void new_order_with_valid_MEQ_and_minimum_quantity_trade_passes() {
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.BUY, 2000, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 500));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher).publishMany(argumentCaptor.capture());
        List<Event> events = argumentCaptor.getValue();
        assertTrue(events.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent
                        && ((OrderAcceptedEvent) event).getRequestId() == 1
                        && ((OrderAcceptedEvent) event).getOrderId() == 11
        ));
    }


    @Test
    void new_order_request_where_MEQ_is_out_of_range(){
        EnterOrderRq rq = EnterOrderRq.createNewOrderRq(1, security.getIsin(), 11, LocalDateTime.now(),
                Side.SELL, 300, 15450, broker.getBrokerId(), shareholder.getShareholderId(),
                0, 500);
        mockOrderHandler.handleEnterOrder(rq);
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(11);
        assertThat(outputEvent.getErrors()).containsOnly(Message.INVALID_MINIMUM_EXECUTION_QUANTITY_RANGE);
    }

    @Test
    void order_update_where_MEQ_should_not_change(){
        MEQOrder newOrder = MEQOrder.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(304)
                .price(15700)
                .broker(broker)
                .shareholder(shareholder)
                .minimumExecutionQuantity(100)
                .build();

        newOrder.updateFromRequest(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.BUY, 400, 15450, broker.getBrokerId(), 0, 0,
                200));

        assertThat(newOrder.getMinimumExecutionQuantity()).isEqualTo(100);
    }

    @Test
    void order_update_where_update_request_does_not_change_MEQ(){
        MEQOrder newOrder = MEQOrder.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(304)
                .price(15700)
                .broker(broker)
                .shareholder(shareholder)
                .minimumExecutionQuantity(100)
                .build();

        newOrder.updateFromRequest(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 11, LocalDateTime.now(),
                Side.BUY, 400, 15450, broker.getBrokerId(), 0, 0, 100));

        assertThat(newOrder.getMinimumExecutionQuantity()).isEqualTo(100);
    }

    @Test
    void new_order_where_order_does_not_match(){
        MEQOrder newOrder = MEQOrder.builder()
                .orderId(11)
                .security(security)
                .side(Side.BUY)
                .quantity(304)
                .price(300)
                .broker(broker)
                .shareholder(shareholder)
                .minimumExecutionQuantity(100)
                .build();
        OrderBook baseOrderBook = security.getOrderBook();
        LinkedList<MatchResult> results =  matcher.execute(newOrder, newOrder.getMinimumExecutionQuantity());;
        assertThat(results).hasSize(1);
        MatchResult result = results.get(0);
        assertThat(result).isEqualTo(MatchResult.minimumExecutionQuantityNotMet(newOrder));
        assertThat(security.getOrderBook()).isEqualTo(baseOrderBook);
    }
}
