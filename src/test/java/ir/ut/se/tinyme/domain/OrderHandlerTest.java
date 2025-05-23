package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.config.MockedJMSTestConfig;
import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.domain.service.OrderHandler;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.TradeDTO;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class OrderHandlerTest {
    @Autowired
    OrderHandler orderHandler;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    private Security security;
    private Shareholder shareholder;
    private Broker broker1;
    private Broker broker2;
    private Broker broker3;


    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker1 = Broker.builder().brokerId(1).build();
        broker2 = Broker.builder().brokerId(2).build();
        broker3 = Broker.builder().brokerId(2).build();
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);
        brokerRepository.addBroker(broker3);
    }
    @Test
    void new_order_matched_completely_with_one_trade() {
        Order matchingBuyOrder = Order.builder()
                .orderId(100)
                .security(security)
                .side(Side.BUY)
                .quantity(1000)
                .price(15500)
                .broker(broker1)
                .shareholder(shareholder)
                .build();
        Order incomingSellOrder = Order.builder()
                .orderId(200)
                .security(security)
                .side(Side.SELL)
                .quantity(300)
                .price(15450)
                .broker(broker2)
                .shareholder(shareholder)
                .build();

        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC",
                200, LocalDateTime.now(), Side.SELL, 300, 15450, 2,
                shareholder.getShareholderId(), 0, 0));

        Trade trade = new Trade(security, matchingBuyOrder.getPrice(), incomingSellOrder.getQuantity(),
                matchingBuyOrder, incomingSellOrder);
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent &&
                        ((OrderAcceptedEvent) event).getOrderId() == 200 &&
                        ((OrderAcceptedEvent) event).getRequestId() == 1
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderExecutedEvent &&
                        ((OrderExecutedEvent) event).getOrderId() == 200 &&
                        ((OrderExecutedEvent) event).getRequestId() == 1 &&
                        Objects.equals(((OrderExecutedEvent) event).getTrades(), List.of(new TradeDTO(trade)))
        ));
    }

    @Test
    void new_order_queued_with_no_trade() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC",
                200, LocalDateTime.now(), Side.SELL, 300, 15450, 2,
                shareholder.getShareholderId(), 0, 0));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent &&
                        ((OrderAcceptedEvent) event).getOrderId() == 200 &&
                        ((OrderAcceptedEvent) event).getRequestId() == 1
        ));
    }

    @Test
    void new_order_matched_partially_with_two_trades() {
        Order matchingBuyOrder1 = Order.builder()
                .orderId(100)
                .security(security)
                .side(Side.BUY)
                .quantity(300)
                .price(15500)
                .broker(broker1)
                .shareholder(shareholder)
                .build();
        Order matchingBuyOrder2 = Order.builder()
                .orderId(110)
                .security(security)
                .side(Side.BUY)
                .quantity(300)
                .price(15500)
                .broker(broker1)
                .shareholder(shareholder)
                .build();
        Order incomingSellOrder = Order.builder()
                .orderId(200)
                .security(security)
                .side(Side.SELL)
                .quantity(1000)
                .price(14450)
                .broker(broker2)
                .shareholder(shareholder)
                .build();
        security.getOrderBook().enqueue(matchingBuyOrder1);
        security.getOrderBook().enqueue(matchingBuyOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,
                incomingSellOrder.getSecurity().getIsin(),
                incomingSellOrder.getOrderId(),
                incomingSellOrder.getEntryTime(),
                incomingSellOrder.getSide(),
                incomingSellOrder.getTotalQuantity(),
                incomingSellOrder.getPrice(),
                incomingSellOrder.getBroker().getBrokerId(),
                incomingSellOrder.getShareholder().getShareholderId(), 0, 0));

        Trade trade1 = new Trade(security, matchingBuyOrder1.getPrice(), matchingBuyOrder1.getQuantity(),
                matchingBuyOrder1, incomingSellOrder);
        Trade trade2 = new Trade(security, matchingBuyOrder2.getPrice(), matchingBuyOrder2.getQuantity(),
                matchingBuyOrder2, incomingSellOrder.snapshotWithQuantity(700));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent &&
                        ((OrderAcceptedEvent) event).getOrderId() == 200 &&
                        ((OrderAcceptedEvent) event).getRequestId() == 1
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderExecutedEvent &&
                        ((OrderExecutedEvent) event).getOrderId() == 200 &&
                        ((OrderExecutedEvent) event).getRequestId() == 1 &&
                        Objects.equals(((OrderExecutedEvent) event).getTrades(), List.of(new TradeDTO(trade1), new TradeDTO(trade2)))
        ));
    }

    @Test
    void iceberg_order_behaves_normally_before_being_queued() {
        Order matchingBuyOrder = Order.builder()
                .orderId(100)
                .security(security)
                .side(Side.BUY)
                .quantity(1000)
                .price(15500)
                .broker(broker1)
                .shareholder(shareholder)
                .build();
        Order incomingSellOrder = Order.builder()
                .orderId(200)
                .security(security)
                .side(Side.SELL)
                .quantity(300)
                .price(15450)
                .broker(broker2)
                .shareholder(shareholder)
                .build();
        security.getOrderBook().enqueue(matchingBuyOrder);
        Trade trade = new Trade(security, matchingBuyOrder.getPrice(), incomingSellOrder.getQuantity(),
                matchingBuyOrder, incomingSellOrder);

        EventPublisher mockEventPublisher = mock(EventPublisher.class, withSettings().verboseLogging());
        OrderHandler myOrderHandler = new OrderHandler(securityRepository, brokerRepository, shareholderRepository, mockEventPublisher, new Matcher());
        myOrderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1,
                incomingSellOrder.getSecurity().getIsin(),
                incomingSellOrder.getOrderId(),
                incomingSellOrder.getEntryTime(),
                incomingSellOrder.getSide(),
                incomingSellOrder.getTotalQuantity(),
                incomingSellOrder.getPrice(),
                incomingSellOrder.getBroker().getBrokerId(),
                incomingSellOrder.getShareholder().getShareholderId(), 100, 0));

        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent &&
                        ((OrderAcceptedEvent) event).getOrderId() == 200 &&
                        ((OrderAcceptedEvent) event).getRequestId() == 1
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderExecutedEvent &&
                        ((OrderExecutedEvent) event).getOrderId() == 200 &&
                        ((OrderExecutedEvent) event).getRequestId() == 1 &&
                        Objects.equals(((OrderExecutedEvent) event).getTrades(), List.of(new TradeDTO(trade)))
        ));
    }

    @Test
    void invalid_new_order_with_multiple_errors() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "XXX", -1,
                LocalDateTime.now(), Side.SELL, 0, 0, -1, -1, 0,
                0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(-1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.UNKNOWN_SECURITY_ISIN,
                Message.INVALID_ORDER_ID,
                Message.ORDER_PRICE_NOT_POSITIVE,
                Message.ORDER_QUANTITY_NOT_POSITIVE,
                Message.INVALID_PEAK_SIZE,
                Message.UNKNOWN_BROKER_ID,
                Message.UNKNOWN_SHAREHOLDER_ID
        );
    }

    @Test
    void invalid_new_order_with_tick_and_lot_size_errors() {
        Security aSecurity = Security.builder().isin("XXX").lotSize(10).tickSize(10).build();
        securityRepository.addSecurity(aSecurity);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "XXX", 1,
                LocalDateTime.now(), Side.SELL, 12, 1001, 1, shareholder.getShareholderId(),
                0, 0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE,
                Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE
        );
    }

    @Test
    void update_order_causing_no_trades() {
        Order queuedOrder = Order.builder()
                .orderId(200)
                .security(security)
                .side(Side.SELL)
                .quantity(500)
                .price(15450)
                .broker(broker1)
                .shareholder(shareholder)
                .build();

        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.SELL, 1000, 15450, 1, shareholder.getShareholderId(),
                0, 0));
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderUpdatedEvent &&
                        ((OrderUpdatedEvent) event).getOrderId() == 200 &&
                        ((OrderUpdatedEvent) event).getRequestId() == 1
        ));
    }

    @Test
    void handle_valid_update_with_trades() {
        Order matchingOrder = Order.builder()
                .orderId(1)
                .security(security)
                .side(Side.BUY)
                .quantity(500)
                .price(15450)
                .broker(broker1)
                .shareholder(shareholder)
                .build();
        Order beforeUpdate = Order.builder()
                .orderId(200)
                .security(security)
                .side(Side.SELL)
                .quantity(1000)
                .price(15455)
                .broker(broker2)
                .shareholder(shareholder)
                .build();
        Order afterUpdate = Order.builder()
                .orderId(200)
                .security(security)
                .side(Side.SELL)
                .quantity(500)
                .price(15450)
                .broker(broker2)
                .shareholder(shareholder)
                .build();

        security.getOrderBook().enqueue(matchingOrder);
        security.getOrderBook().enqueue(beforeUpdate);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.SELL, 1000, 15450, broker2.getBrokerId(),
                shareholder.getShareholderId(), 0, 0));

        Trade trade = new Trade(security, 15450, 500, matchingOrder, afterUpdate);
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderUpdatedEvent &&
                        ((OrderUpdatedEvent) event).getOrderId() == 200 &&
                        ((OrderUpdatedEvent) event).getRequestId() == 1
        ));
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderExecutedEvent &&
                        ((OrderExecutedEvent) event).getOrderId() == 200 &&
                        ((OrderExecutedEvent) event).getRequestId() == 1 &&
                        Objects.equals(((OrderExecutedEvent) event).getTrades(), List.of(new TradeDTO(trade)))
        ));
    }

    @Test
    void invalid_update_with_order_id_not_found() {
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.SELL, 1000, 15450, 1, shareholder.getShareholderId(),
                0, 0));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, any()));
    }

    @Test
    void invalid_update_with_multiple_errors() {
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "XXX", -1,
                LocalDateTime.now(), Side.SELL, 0, 0, -1, shareholder.getShareholderId(),
                0, 0));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(-1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.UNKNOWN_SECURITY_ISIN,
                Message.UNKNOWN_BROKER_ID,
                Message.INVALID_ORDER_ID,
                Message.ORDER_PRICE_NOT_POSITIVE,
                Message.ORDER_QUANTITY_NOT_POSITIVE,
                Message.INVALID_PEAK_SIZE
        );
    }

    @Test
    void invalid_delete_with_order_id_not_found() {
        Broker buyBroker = Broker.builder().credit(1_000_000).build();
        brokerRepository.addBroker(buyBroker);
        Order queuedOrder = Order.builder()
                .orderId(200)
                .security(security)
                .side(Side.BUY)
                .quantity(1000)
                .price(15500)
                .broker(buyBroker)
                .shareholder(shareholder)
                .build();

        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, "ABC", Side.SELL, 100));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 100, List.of(Message.ORDER_ID_NOT_FOUND)));
        assertThat(buyBroker.getCredit()).isEqualTo(1_000_000);
    }

    @Test
    void invalid_delete_order_with_non_existing_security() {
        Order queuedOrder = Order.builder()
                .orderId(200)
                .security(security)
                .side(Side.BUY)
                .quantity(1000)
                .price(15500)
                .broker(broker1)
                .shareholder(shareholder)
                .build();

        security.getOrderBook().enqueue(queuedOrder);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, "XXX", Side.SELL, 200));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, List.of(Message.UNKNOWN_SECURITY_ISIN)));
    }

    @Test
    void new_sell_order_without_enough_positions_is_rejected() {
        List<Order> orders = Arrays.asList(
                Order.builder()
                        .orderId(1)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(304)
                        .price(570)
                        .broker(broker3)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(2)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(430)
                        .price(550)
                        .broker(broker3)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(3)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(445)
                        .price(545)
                        .broker(broker3)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(6)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(350)
                        .price(580)
                        .broker(broker1)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(7)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(100)
                        .price(581)
                        .broker(broker2)
                        .shareholder(shareholder)
                        .build()
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.SELL, 400, 590, broker1.getBrokerId(),
                shareholder.getShareholderId(), 0, 0));

        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderRejectedEvent &&
                        ((OrderRejectedEvent) event).getOrderId() == 200 &&
                        ((OrderRejectedEvent) event).getRequestId() == 1 &&
                        Objects.equals(((OrderRejectedEvent) event).getErrors(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS))
        ));
    }

    @Test
    void update_sell_order_without_enough_positions_is_rejected() {
        List<Order> orders = Arrays.asList(
                Order.builder()
                        .orderId(1)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(304)
                        .price(570)
                        .broker(broker3)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(2)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(430)
                        .price(550)
                        .broker(broker3)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(3)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(445)
                        .price(545)
                        .broker(broker3)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(6)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(350)
                        .price(580)
                        .broker(broker1)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(7)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(100)
                        .price(581)
                        .broker(broker2)
                        .shareholder(shareholder)
                        .build()
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6,
                LocalDateTime.now(), Side.SELL, 450, 580, broker1.getBrokerId(),
                shareholder.getShareholderId(), 0, 0));

        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderRejectedEvent &&
                        ((OrderRejectedEvent) event).getOrderId() == 6 &&
                        ((OrderRejectedEvent) event).getRequestId() == 1 &&
                        Objects.equals(((OrderRejectedEvent) event).getErrors(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS))
        ));
    }

    @Test
    void update_sell_order_with_enough_positions_is_executed() {
        Shareholder shareholder1 = Shareholder.builder().build();
        shareholder1.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder1);
        List<Order> orders = Arrays.asList(
                Order.builder()
                        .orderId(1)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(304)
                        .price(570)
                        .broker(broker3)
                        .shareholder(shareholder1)
                        .build(),
                Order.builder()
                        .orderId(2)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(430)
                        .price(550)
                        .broker(broker3)
                        .shareholder(shareholder1)
                        .build(),
                Order.builder()
                        .orderId(3)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(445)
                        .price(545)
                        .broker(broker3)
                        .shareholder(shareholder1)
                        .build(),
                Order.builder()
                        .orderId(6)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(350)
                        .price(580)
                        .broker(broker1)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(7)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(100)
                        .price(581)
                        .broker(broker2)
                        .shareholder(shareholder)
                        .build()
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 6,
                LocalDateTime.now(), Side.SELL, 250, 570, broker1.getBrokerId(),
                shareholder.getShareholderId(), 0, 0));

        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderExecutedEvent
        ));
        assertThat(shareholder1.hasEnoughPositionsOn(security, 100_000 + 250)).isTrue();
        assertThat(shareholder.hasEnoughPositionsOn(security, 99_500 - 251)).isFalse();
    }

    @Test
    void new_buy_order_does_not_check_for_position() {
        Shareholder shareholder1 = Shareholder.builder().build();
        shareholder1.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder1);
        List<Order> orders = Arrays.asList(
                Order.builder()
                        .orderId(1)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(304)
                        .price(570)
                        .broker(broker3)
                        .shareholder(shareholder1)
                        .build(),
                Order.builder()
                        .orderId(2)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(430)
                        .price(550)
                        .broker(broker3)
                        .shareholder(shareholder1)
                        .build(),
                Order.builder()
                        .orderId(3)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(445)
                        .price(545)
                        .broker(broker3)
                        .shareholder(shareholder1)
                        .build(),
                Order.builder()
                        .orderId(6)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(350)
                        .price(580)
                        .broker(broker1)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(7)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(100)
                        .price(581)
                        .broker(broker2)
                        .shareholder(shareholder)
                        .build()
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200,
                LocalDateTime.now(), Side.BUY, 500, 570, broker3.getBrokerId(),
                shareholder.getShareholderId(), 0, 0));

        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent
        ));
        assertThat(shareholder1.hasEnoughPositionsOn(security, 100_000)).isTrue();
        assertThat(shareholder.hasEnoughPositionsOn(security, 500)).isTrue();
    }

    @Test
    void update_buy_order_does_not_check_for_position() {
        Shareholder shareholder1 = Shareholder.builder().build();
        shareholder1.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder1);
        List<Order> orders = Arrays.asList(
                Order.builder()
                        .orderId(1)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(304)
                        .price(570)
                        .broker(broker3)
                        .shareholder(shareholder1)
                        .build(),
                Order.builder()
                        .orderId(2)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(430)
                        .price(550)
                        .broker(broker3)
                        .shareholder(shareholder1)
                        .build(),
                Order.builder()
                        .orderId(3)
                        .security(security)
                        .side(Side.BUY)
                        .quantity(445)
                        .price(545)
                        .broker(broker3)
                        .shareholder(shareholder1)
                        .build(),
                Order.builder()
                        .orderId(6)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(350)
                        .price(580)
                        .broker(broker1)
                        .shareholder(shareholder)
                        .build(),
                Order.builder()
                        .orderId(7)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(100)
                        .price(581)
                        .broker(broker2)
                        .shareholder(shareholder)
                        .build()
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        shareholder.decPosition(security, 99_500);
        broker3.increaseCreditBy(100_000_000);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 3,
                LocalDateTime.now(), Side.BUY, 500, 545, broker3.getBrokerId(),
                shareholder1.getShareholderId(), 0, 0));

        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher, times(1)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OrderAcceptedEvent
        ));
        assertThat(shareholder1.hasEnoughPositionsOn(security, 100_000)).isTrue();
        assertThat(shareholder.hasEnoughPositionsOn(security, 500)).isTrue();
    }
}
