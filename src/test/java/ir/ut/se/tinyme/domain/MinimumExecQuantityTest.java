package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.domain.service.OrderHandler;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.Message;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
                new Order(1, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, broker, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder),
                new Order(10, security, Side.SELL, 65, 200, broker, shareholder)
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
        verify(mockEventPublisher).publish((new OrderAcceptedEvent(1, 11)));
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
        Order newOrder = new Order(11, security, Side.BUY, 304, 15700, broker, shareholder,
                LocalDateTime.now() ,100);

        newOrder.updateFromRequest(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 11,
                LocalDateTime.now(), Side.BUY, 400, 15450, broker.getBrokerId(), 0, 0,
                200));

        assertThat(newOrder.getMinimumExecutionQuantity()).isEqualTo(100);
    }

    @Test
    void order_update_where_update_request_does_not_change_MEQ(){
        Order newOrder = new Order(11, security, Side.BUY, 304, 15700, broker, shareholder,
                LocalDateTime.now() ,100);

        newOrder.updateFromRequest(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 11, LocalDateTime.now(),
                Side.BUY, 400, 15450, broker.getBrokerId(), 0, 0, 100));

        assertThat(newOrder.getMinimumExecutionQuantity()).isEqualTo(100);
    }

    @Test
    void new_order_where_order_does_not_matches(){
        Order newOrder = new Order(11, security, Side.BUY, 304, 300, broker, shareholder,
                LocalDateTime.now() ,100);
        OrderBook baseOrderBook = security.getOrderBook();
        MatchResult matchResult = matcher.execute(newOrder, newOrder.getMinimumExecutionQuantity());
        assertThat(matchResult).isEqualTo(MatchResult.minimumExecutionQuantityNotMet());
        assertThat(security.getOrderBook()).isEqualTo(baseOrderBook);
    }
}
