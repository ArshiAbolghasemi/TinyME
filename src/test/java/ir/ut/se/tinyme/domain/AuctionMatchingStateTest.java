package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.domain.service.MatcherHandler;
import ir.ut.se.tinyme.domain.service.OrderHandler;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.domain.service.MatcherHandler;
import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.event.*;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.messaging.request.MatcherState;
import ir.ut.se.tinyme.messaging.request.MatchingStateRq;
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

import static ir.ut.se.tinyme.messaging.request.MatchingStateRq.CreateNewMatchingStateRq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext
public class AuctionMatchingStateTest {
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

    private MatcherHandler mockMatcherHandler;


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
        mockMatcherHandler = new MatcherHandler(securityRepository, brokerRepository, shareholderRepository,
                mockEventPublisher, matcher);
    }

    @Test
    void Change_Matching_State_Test(){
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);

        ArgumentCaptor<SecurityStateChangedEvent> securityStateChangedEventArgumentCaptor = ArgumentCaptor.forClass
                (SecurityStateChangedEvent.class);
        verify(mockEventPublisher).publish(securityStateChangedEventArgumentCaptor.capture());
        SecurityStateChangedEvent outputEvent = securityStateChangedEventArgumentCaptor.getValue();
        assertThat(outputEvent.getSecurityIsin()).isEqualTo(security.getIsin());
        assertThat(outputEvent.getMatcherState()).isEqualTo(security.getState());
    }

}


