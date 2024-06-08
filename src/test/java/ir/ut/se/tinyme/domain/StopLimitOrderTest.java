package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.domain.service.OrderHandler;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.event.*;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
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
import org.mockito.InOrder;
import ir.ut.se.tinyme.messaging.TradeDTO;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext
public class StopLimitOrderTest {

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
        security = Security.builder().isin("ABC").lastTradePrice(10).build();
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
                        .price(200)
                        .broker(broker)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
                        .build(),
                Order.builder()
                        .orderId(19)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(60)
                        .price(400)
                        .broker(broker)
                        .shareholder(shareholder)
                        .build()
        );
        orders.forEach(order -> orderBook.enqueue(order));
        mockEventPublisher = mock(EventPublisher.class, withSettings().verboseLogging());
        mockOrderHandler = new OrderHandler(securityRepository, brokerRepository, shareholderRepository,
                mockEventPublisher, matcher);
    }

    @Test
    void new_order_request_where_Stop_Price_is_out_of_range(){
        EnterOrderRq rq = EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11, LocalDateTime.now(),
                Side.SELL, 300, 15450, broker.getBrokerId(), shareholder.getShareholderId(),
                0, 0, -10);
        mockOrderHandler.handleEnterOrder(rq);
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(11);
        assertThat(outputEvent.getErrors()).containsOnly(Message.INVALID_STOP_PRICE_VALUE);
    }

    @Test
    void new_order_request_where_Iceberg_order_has_stop_price(){
        EnterOrderRq rq = EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11, LocalDateTime.now(),
                Side.SELL, 300, 15450, broker.getBrokerId(), shareholder.getShareholderId(),
                100, 0, 15000);
        mockOrderHandler.handleEnterOrder(rq);
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(11);
        assertThat(outputEvent.getErrors()).containsOnly(Message.ICEBERG_ORDERS_CANT_BE_STOP_PRICE_ORDERS);
    }

    @Test
    void new_order_request_where_MEQ_order_has_stop_price(){
        EnterOrderRq rq = EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11, LocalDateTime.now(),
                Side.SELL, 300, 15450, broker.getBrokerId(), shareholder.getShareholderId(),
                0, 50, 15000);
        mockOrderHandler.handleEnterOrder(rq);
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(11);
        assertThat(outputEvent.getErrors()).containsOnly(Message.MEQ_ORDERS_CANT_BE_STOP_PRICE_ORDERS);
    }

    @Test
    void new_order_request_where_Iceberg_order_has_MEQ_and_stop_price(){
        EnterOrderRq rq = EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11, LocalDateTime.now(),
                Side.SELL, 300, 15450, broker.getBrokerId(), shareholder.getShareholderId(),
                100, 50, 15000);
        mockOrderHandler.handleEnterOrder(rq);
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(11);
        assertThat(outputEvent.getErrors()).contains(
                Message.MEQ_ORDERS_CANT_BE_STOP_PRICE_ORDERS,
                Message.ICEBERG_ORDERS_CANT_BE_STOP_PRICE_ORDERS
        );
    }

    @Test
    void new_order_with_valid_stop_price() {
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.SELL, 200, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 15800));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent &&
                        ((OrderAcceptedEvent) event).getOrderId() == 11 &&
                        ((OrderAcceptedEvent) event).getRequestId() == 1
        ));
    }

    @Test
    void new_stop_limit_order_where_it_activates_when_added(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.SELL, 200, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 15000));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderActivatedEvent &&
                        ((OrderActivatedEvent) event).getOrderId() == 11
        ));
    }

    @Test
    void new_stop_limit_order_where_it_activates_when_added_and_executes_test(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.SELL, 200, 15500, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 15000));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderActivatedEvent &&
                        ((OrderActivatedEvent) event).getRqId() == 1
        ));
    }

    @Test
    void inactive_stop_limit_order_list_sell_sort_function_test(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.SELL, 5, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 5));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.SELL, 3, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 1));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(2, security.getIsin(), 12,
                LocalDateTime.now(), Side.SELL, 4, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 2));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(3, security.getIsin(), 13,
                LocalDateTime.now(), Side.SELL, 5, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 2));

        assertThat(security.getStopLimitOrderList().getSellQueue().get(0).getOrderId()).isEqualTo(14);
        assertThat(security.getStopLimitOrderList().getSellQueue().get(1).getOrderId()).isEqualTo(12);
        assertThat(security.getStopLimitOrderList().getSellQueue().get(2).getOrderId()).isEqualTo(13);
        assertThat(security.getStopLimitOrderList().getSellQueue().get(3).getOrderId()).isEqualTo(11);
    }

    @Test
    void inactive_stop_limit_order_list_buy_sort_function_test(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.BUY, 5, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 50));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.BUY, 3, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 300));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(2, security.getIsin(), 12,
                LocalDateTime.now(), Side.BUY, 4, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 200));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(3, security.getIsin(), 13,
                LocalDateTime.now(), Side.BUY, 5, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 200));

        assertThat(security.getStopLimitOrderList().getBuyQueue().get(0).getOrderId()).isEqualTo(14);
        assertThat(security.getStopLimitOrderList().getBuyQueue().get(1).getOrderId()).isEqualTo(12);
        assertThat(security.getStopLimitOrderList().getBuyQueue().get(2).getOrderId()).isEqualTo(13);
        assertThat(security.getStopLimitOrderList().getBuyQueue().get(3).getOrderId()).isEqualTo(11);
    }

    @Test
    void check_broker_credit_after_new_stop_price_buy_order() {
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.BUY, 200, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 15800));
       assertThat(broker.getCredit()).isEqualTo(100_000_000L - 15820*200);
    }

    @Test
    void check_broker_credit_after_activation() {
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.BUY, 200, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 15800));
        assertThat(broker.getCredit()).isEqualTo(100_000_000L - 15820*200);
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.BUY, 200, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 0));
        assertThat(broker.getCredit()).isEqualTo(100_000_000L);

    }

    @Test
    void check_broker_credit_after_new_stop_price_sell_order() {
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.SELL, 200, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 2));
        assertThat(broker.getCredit()).isEqualTo(100_000_000L);
    }

    @Test
    void check_activation_after_some_trades_happen_test(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 11,
                LocalDateTime.now(), Side.BUY, 65, 200, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 0));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(5, security.getIsin(), 12,
                LocalDateTime.now(), Side.BUY, 5, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 300));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(6, security.getIsin(), 13,
                LocalDateTime.now(), Side.BUY, 30, 400, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 0));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(3)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        long executionEventCount = capturedEvents.stream()
                .filter(event -> event instanceof OrderExecutedEvent)
                .count();

        long acceptEventCount = capturedEvents.stream()
                .filter(event -> event instanceof OrderAcceptedEvent)
                .count();

        long orderActivatedEventCount = capturedEvents.stream()
                .filter(event -> event instanceof OrderActivatedEvent)
                .count();

        // Verify the counts
        assertEquals(3, executionEventCount);
        assertEquals(3, acceptEventCount);
        assertEquals(1, orderActivatedEventCount);
    }

    @Test
    void check_update_non_stoplimit_order_with_stopprice(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.SELL, 5, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 0));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(5, security.getIsin(), 14,
                LocalDateTime.now(), Side.SELL, 5, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 5));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(14);
        assertThat(outputEvent.getErrors()).containsOnly(Message.COULD_NOT_UPDATE_STOP_LIMIT_PRICE_FOR_NON_LIMIT_PRICE_ORDER_OR_NON_ACTIVE_STOPLIMIT_ORDER);
    }

    @Test
    void check_update_stoplimit_order_which_is_active_using_stopprice(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.SELL, 5, 15790, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        assertThat(security.getStopLimitOrderList().getSellQueue().isEmpty());
        assertThat(security.getOrderBook().getSellQueue().get(2).getOrderId()).isEqualTo(14);
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(5, security.getIsin(), 14,
                LocalDateTime.now(), Side.SELL, 5, 15790, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 5));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(14);
        assertThat(outputEvent.getErrors()).contains(Message.COULD_NOT_UPDATE_STOP_LIMIT_PRICE_FOR_NON_LIMIT_PRICE_ORDER_OR_NON_ACTIVE_STOPLIMIT_ORDER);
    }

    @Test
    void check_update_quantity_of_a_sell_stoplimit_order_which_is_active(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.SELL, 5, 15790, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        assertThat(security.getStopLimitOrderList().getSellQueue().isEmpty());
        assertThat(security.getOrderBook().getSellQueue().get(2).getQuantity()).isEqualTo(5);
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(5, security.getIsin(), 14,
                LocalDateTime.now(), Side.SELL, 10, 15790, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 0));
        assertThat(security.getOrderBook().getSellQueue().get(2).getQuantity()).isEqualTo(10);
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(2)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderUpdatedEvent &&
                        ((OrderUpdatedEvent) event).getOrderId() == 14
        ));
    }

    @Test
    void check_update_quantity_of_a_buy_stoplimit_order_which_is_inactive(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.BUY, 5, 150, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(5, security.getIsin(), 14,
                LocalDateTime.now(), Side.BUY, 15, 150, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(2)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderUpdatedEvent &&
                        ((OrderUpdatedEvent) event).getOrderId() == 14
        ));
        assertThat(broker.getCredit()).isEqualTo(100_000_000L - 15 * 150);
    }


    @Test
    void check_update_price_of_a_stoplimit_order_which_is_inactive(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.BUY, 5, 150, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(5, security.getIsin(), 14,
                LocalDateTime.now(), Side.BUY, 5, 200, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(2)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderUpdatedEvent &&
                        ((OrderUpdatedEvent) event).getOrderId() == 14
        ));
        assertThat(security.getStopLimitOrderList().findByOrderId(Side.BUY,14).getRqId()).isEqualTo(5);
        assertThat(broker.getCredit()).isEqualTo(100_000_000L - 5 * 200);
    }

    @Test
    void check_delete_a_stoplimit_order_which_is_inactive(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.BUY, 5, 150, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        mockOrderHandler.handleDeleteOrder( new DeleteOrderRq( 6, security.getIsin(), Side.BUY, 14));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(2)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        assertThat(security.getStopLimitOrderList().getBuyQueue().isEmpty());
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderDeletedEvent &&
                        ((OrderDeletedEvent) event).getOrderId() == 14
        ));

    }

    @Test
    void check_delete_a_stoplimit_order_which_is_active(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.BUY, 5, 150, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 9));
        mockOrderHandler.handleDeleteOrder( new DeleteOrderRq( 6, security.getIsin(), Side.BUY, 14));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(2)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderDeletedEvent &&
                        ((OrderDeletedEvent) event).getOrderId() == 14
        ));
        assertThat(security.getOrderBook().getBuyQueue().size()).isEqualTo(5);
    }

    @Test
    void new_stop_limit_order_where_it_activates_when_added_and_executes_events_order_test() {
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.SELL, 200, 15500, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 15000));
        TradeDTO t = new TradeDTO(security.getIsin(),15700,200,1,11);
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        InOrder inOrder = inOrder(mockEventPublisher);
        inOrder.verify(mockEventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent &&
                        ((OrderAcceptedEvent) event).getRequestId() == 1 &&
                        ((OrderAcceptedEvent) event).getOrderId() == 11
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderActivatedEvent &&
                        ((OrderActivatedEvent) event).getRqId() == 1 &&
                        ((OrderActivatedEvent) event).getOrderId() == 11
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderExecutedEvent &&
                        ((OrderExecutedEvent) event).getOrderId() == 11 &&
                        ((OrderExecutedEvent) event).getRequestId() == 1 &&
                        Objects.equals(((OrderExecutedEvent) event).getTrades(), List.of(t))
        ));
    }

    @Test
    void check_event_order_after_some_trades_happen_test(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 11,
                LocalDateTime.now(), Side.BUY, 65, 200, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 0));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(5, security.getIsin(), 12,
                LocalDateTime.now(), Side.BUY, 5, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 300));
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(6, security.getIsin(), 13,
                LocalDateTime.now(), Side.BUY, 30, 400, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 0));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        InOrder inOrder = inOrder(mockEventPublisher);
        inOrder.verify(mockEventPublisher, times(3)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        long executionEventCount = capturedEvents.stream()
                .filter(event -> event instanceof OrderExecutedEvent)
                .count();

        long acceptEventCount = capturedEvents.stream()
                .filter(event -> event instanceof OrderAcceptedEvent)
                .count();

        long orderActivatedEventCount = capturedEvents.stream()
                .filter(event -> event instanceof OrderActivatedEvent)
                .count();

        assertEquals(3, executionEventCount);
        assertEquals(3, acceptEventCount);
        assertEquals(1, orderActivatedEventCount);

        TradeDTO t1 = new TradeDTO(security.getIsin(),200,65,11,10);
        TradeDTO t2 = new TradeDTO(security.getIsin(),400,30,13,19);
        TradeDTO t3 = new TradeDTO(security.getIsin(),400,5,12,19);


        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent &&
                        ((OrderAcceptedEvent) event).getRequestId() == 4 &&
                        ((OrderAcceptedEvent) event).getOrderId() == 11
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent &&
                        ((OrderAcceptedEvent) event).getRequestId() == 5 &&
                        ((OrderAcceptedEvent) event).getOrderId() == 12
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent &&
                        ((OrderAcceptedEvent) event).getRequestId() == 6 &&
                        ((OrderAcceptedEvent) event).getOrderId() == 13
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderActivatedEvent &&
                        ((OrderActivatedEvent) event).getRqId() == 5 &&
                        ((OrderActivatedEvent) event).getOrderId() == 12
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderExecutedEvent &&
                        ((OrderExecutedEvent) event).getOrderId() == 11 &&
                        ((OrderExecutedEvent) event).getRequestId() == 4 &&
                        Objects.equals(((OrderExecutedEvent) event).getTrades(), List.of(t1))
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderExecutedEvent &&
                        ((OrderExecutedEvent) event).getOrderId() == 13 &&
                        ((OrderExecutedEvent) event).getRequestId() == 6 &&
                        Objects.equals(((OrderExecutedEvent) event).getTrades(), List.of(t2))
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderExecutedEvent &&
                        ((OrderExecutedEvent) event).getOrderId() == 12 &&
                        ((OrderExecutedEvent) event).getRequestId() == 5 &&
                        Objects.equals(((OrderExecutedEvent) event).getTrades(), List.of(t3))
        ));
        inOrder.verifyNoMoreInteractions();
    }

}





