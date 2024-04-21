package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.domain.service.OrderHandler;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.event.*;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                new Order(1, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, broker, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder),
                new Order(10, security, Side.SELL, 65, 200, broker, shareholder),
                new Order(19, security, Side.SELL, 60, 400, broker, shareholder)
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
        verify(mockEventPublisher).publish((new OrderAcceptedEvent(1, 11)));
    }

    @Test
    void new_stop_limit_order_where_it_activates_when_added(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.SELL, 200, 15820, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 15000));
        ArgumentCaptor<OrderActivatedEvent> orderActivatedCaptor = ArgumentCaptor.forClass(OrderActivatedEvent.class);
        ArgumentCaptor<OrderAcceptedEvent> orderAcceptedCaptor = ArgumentCaptor.forClass(OrderAcceptedEvent.class);
        verify(mockEventPublisher).publish(orderActivatedCaptor.capture());
        verify(mockEventPublisher).publish(orderAcceptedCaptor.capture());
        OrderActivatedEvent outputEvent = orderActivatedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(11);
    }

    @Test
    void new_stop_limit_order_where_it_activates_when_added_and_executes_test(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.SELL, 200, 15500, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 15000));
        ArgumentCaptor<OrderActivatedEvent> orderActivatedCaptor = ArgumentCaptor.forClass(OrderActivatedEvent.class);
        ArgumentCaptor<OrderAcceptedEvent> orderAcceptedCaptor = ArgumentCaptor.forClass(OrderAcceptedEvent.class);
        ArgumentCaptor<OrderExecutedEvent> orderExecutedCaptor = ArgumentCaptor.forClass(OrderExecutedEvent.class);
        verify(mockEventPublisher).publish(orderActivatedCaptor.capture());
        verify(mockEventPublisher).publish(orderAcceptedCaptor.capture());
        verify(mockEventPublisher).publish(orderExecutedCaptor.capture());
        OrderActivatedEvent outputEvent = orderActivatedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(11);
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
        ArgumentCaptor<OrderActivatedEvent> orderActivatedCaptor = ArgumentCaptor.forClass(OrderActivatedEvent.class);
        ArgumentCaptor<OrderAcceptedEvent> orderAcceptedCaptor = ArgumentCaptor.forClass(OrderAcceptedEvent.class);
        ArgumentCaptor<OrderExecutedEvent> orderExecutedCaptor = ArgumentCaptor.forClass(OrderExecutedEvent.class);
        verify(mockEventPublisher).publish(orderActivatedCaptor.capture());
        verify(mockEventPublisher, times(3)).publish(orderAcceptedCaptor.capture());
        verify(mockEventPublisher, times(3)).publish(orderExecutedCaptor.capture());

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
    void check_update_stoplimit_order_which_is_active(){
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.SELL, 5, 15790, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        assertThat(security.getStopLimitOrderList().getSellQueue().isEmpty());
        assertThat(security.getOrderBook().getSellQueue().get(2).getQuantity()).isEqualTo(5);
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(5, security.getIsin(), 14,
                LocalDateTime.now(), Side.SELL, 10, 15790, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 0));
        assertThat(security.getOrderBook().getSellQueue().get(2).getQuantity()).isEqualTo(10);
        ArgumentCaptor<OrderUpdatedEvent> orderUpdatedEventArgumentCaptor = ArgumentCaptor.forClass(OrderUpdatedEvent.class);
        verify(mockEventPublisher).publish(orderUpdatedEventArgumentCaptor.capture());
        OrderUpdatedEvent outputEvent = orderUpdatedEventArgumentCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(14);
    }


}
