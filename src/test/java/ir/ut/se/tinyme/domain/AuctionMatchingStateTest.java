package ir.ut.se.tinyme.domain;

import ir.ut.se.tinyme.domain.entity.*;
import ir.ut.se.tinyme.domain.service.Matcher;
import ir.ut.se.tinyme.domain.service.MatcherHandler;
import ir.ut.se.tinyme.domain.service.OrderHandler;
import ir.ut.se.tinyme.messaging.EventPublisher;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static ir.ut.se.tinyme.messaging.request.MatchingStateRq.CreateNewMatchingStateRq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext
public class AuctionMatchingStateTest {
    private Security security;
    private Broker broker;
    private Broker broker2;
    private Shareholder shareholder;
    private Shareholder shareholder2;
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
        broker2 =Broker.builder().brokerId(2).credit(900_000_000L).build();
        brokerRepository.addBroker(broker);
        brokerRepository.addBroker(broker2);
        shareholder = Shareholder.builder().shareholderId(1).build();
        shareholder.incPosition(security, 100_000);
        shareholder2 = Shareholder.builder().shareholderId(2).build();
        shareholder2.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);
        shareholderRepository.addShareholder(shareholder2);
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
                        .orderId(6)
                        .security(security)
                        .side(Side.SELL)
                        .quantity(350)
                        .price(15810)
                        .broker(broker2)
                        .shareholder(shareholder)
                        .entryTime(LocalDateTime.now())
                        .status(OrderStatus.NEW)
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

    @Test
    void enqueue_new_order_and_get_best_opening_price_test(){
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);

        assertThat(orderBook.getBuyQueue().size()).isEqualTo(2);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(2);
        assertThat(security.getAuctionData().getBestOpeningPrice()).isEqualTo(15700);
        assertThat(security.getAuctionData().getBestQuantity()).isEqualTo(285);
        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(2)).publishMany(argumentCaptor.capture());
        List<Event> capturedEvents = argumentCaptor.getValue();
        assertTrue(capturedEvents.stream().anyMatch(event ->
                event instanceof OpeningPriceEvent &&
                        ((OpeningPriceEvent) event).getOpeningPrice() == 15700 &&
                        ((OpeningPriceEvent) event).getSecurityIsin().equals(security.getIsin()) &&
                        ((OpeningPriceEvent) event).getTradableQuantity() == 285
        ));
    }

    @Test
    void enqueue_new_order_and_best_opening_price_is_invalid_test(){
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15800, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);

        assertThat(orderBook.getBuyQueue().size()).isEqualTo(2);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(2);
        assertThat(security.getAuctionData().getBestOpeningPrice()).isEqualTo(0);
        assertThat(security.getAuctionData().getBestQuantity()).isEqualTo(0);
        ArgumentCaptor<OpeningPriceEvent> openingPriceEventArgumentCaptor = ArgumentCaptor.forClass
                (OpeningPriceEvent.class);
        verify(mockEventPublisher).publish(openingPriceEventArgumentCaptor.capture());
        OpeningPriceEvent outputEvent = openingPriceEventArgumentCaptor.getValue();
        assertThat(outputEvent.getOpeningPrice()).isEqualTo(0);
        assertThat(outputEvent.getSecurityIsin()).isEqualTo(security.getIsin());
        assertThat(outputEvent.getTradableQuantity()).isEqualTo(0);
    }

    @Test
    void new_stop_limit_order_in_auction_state_test()
    {
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewStopOrderRequest(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 15600);
        mockOrderHandler.handleEnterOrder(enterOrderRq);

        assertThat(orderBook.getBuyQueue().size()).isEqualTo(2);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(1);
        assertThat(security.getStopLimitOrderList().getBuyQueue().size()).isEqualTo(0);
        assertThat(security.getStopLimitOrderList().getSellQueue().size()).isEqualTo(0);
        assertThat(security.getAuctionData().getBestOpeningPrice()).isEqualTo(0);
        assertThat(security.getAuctionData().getBestQuantity()).isEqualTo(0);

        ArgumentCaptor<OrderRejectedEvent> orderRejectedEventArgumentCaptor = ArgumentCaptor.forClass
                (OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedEventArgumentCaptor.capture());
        OrderRejectedEvent outputEvent2 = orderRejectedEventArgumentCaptor.getValue();
        assertThat(outputEvent2.getOrderId()).isEqualTo(7);
        assertThat(outputEvent2.getErrors()).containsExactly(Message.CAN_NOT_INITIALIZE_MEQ_OR_STOP_LIMIT_ORDERS_ON_AUCTION_MODE);
        assertThat(outputEvent2.getRequestId()).isEqualTo(1);
    }

    @Test
    void new_meq_order_in_auction_state_test()
    {
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 100);
        mockOrderHandler.handleEnterOrder(enterOrderRq);

        assertThat(orderBook.getBuyQueue().size()).isEqualTo(2);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(1);
        assertThat(security.getAuctionData().getBestOpeningPrice()).isEqualTo(0);
        assertThat(security.getAuctionData().getBestQuantity()).isEqualTo(0);

        ArgumentCaptor<OrderRejectedEvent> orderRejectedEventArgumentCaptor = ArgumentCaptor.forClass
                (OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedEventArgumentCaptor.capture());
        OrderRejectedEvent outputEvent2 = orderRejectedEventArgumentCaptor.getValue();
        assertThat(outputEvent2.getOrderId()).isEqualTo(7);
        assertThat(outputEvent2.getErrors()).containsExactly(Message.CAN_NOT_INITIALIZE_MEQ_OR_STOP_LIMIT_ORDERS_ON_AUCTION_MODE);
        assertThat(outputEvent2.getRequestId()).isEqualTo(1);
    }

    @Test
    void Change_Matching_State_from_auction_to_auction_or_continuous_Test(){
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        MatchingStateRq matchingStateRq2 = CreateNewMatchingStateRq(security.getIsin(), MatcherState.CONTINUOUS);
        MatchingStateRq matchingStateRq3 = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);

        mockMatcherHandler.handleMatchStateRq(matchingStateRq3);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);

        mockMatcherHandler.handleMatchStateRq(matchingStateRq2);
        assertThat(security.getState()).isEqualTo(MatcherState.CONTINUOUS);

        ArgumentCaptor<SecurityStateChangedEvent> securityStateChangedEventArgumentCaptor = ArgumentCaptor.forClass
                (SecurityStateChangedEvent.class);
        verify(mockEventPublisher,times(3)).publish(securityStateChangedEventArgumentCaptor.capture());

        InOrder inOrder = inOrder(mockEventPublisher);
        inOrder.verify(mockEventPublisher,times(2)).publish(new SecurityStateChangedEvent(security.getIsin(), MatcherState.AUCTION));
        inOrder.verify(mockEventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatcherState.CONTINUOUS));
    }

    @Test
    void update_Stop_limit_Order_in_auction_state()
    {
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createNewStopOrderRequest(4, security.getIsin(), 14,
                LocalDateTime.now(), Side.BUY, 5, 150, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        mockOrderHandler.handleEnterOrder(EnterOrderRq.createUpdateStopLimitOrderRq(5, security.getIsin(), 14,
                LocalDateTime.now(), Side.BUY, 5, 200, broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 11));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedEventArgumentCaptor = ArgumentCaptor.forClass
                (OrderRejectedEvent.class);
        verify(mockEventPublisher).publish(orderRejectedEventArgumentCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedEventArgumentCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(14);
        assertThat(outputEvent.getErrors()).containsExactly(Message.CANT_UPDATE_STOP_LIMIT_ORDER_ON_AUCTION_MODE);
        assertThat(outputEvent.getRequestId()).isEqualTo(5);
    }

    @Test
    void enqueue_new_buy_order_and_check_brokers_credit_test(){
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.BUY, 100, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);
        assertThat(broker.getCredit()).isEqualTo(100_000_000L - 100 * 15700);
        assertThat(orderBook.getBuyQueue().size()).isEqualTo(3);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(1);
    }

    @Test
    void check_opening_price_if_an_order_gets_updated() {
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);

        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(), 7, LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);

        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(2, security.getIsin(), 7, LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(updateOrderRq);

        assertThat(orderBook.getBuyQueue().size()).isEqualTo(2);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(2);
        assertThat(security.getAuctionData().getBestOpeningPrice()).isEqualTo(15700);
        assertThat(security.getAuctionData().getBestQuantity()).isEqualTo(285);

        ArgumentCaptor<List<Event>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockEventPublisher, times(3)).publishMany(argumentCaptor.capture());
        List<Event> allCapturedEvents = argumentCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
            assertTrue(allCapturedEvents.stream().anyMatch(event ->
                    event instanceof OpeningPriceEvent &&
                            ((OpeningPriceEvent) event).getOpeningPrice() == 15700 &&
                            ((OpeningPriceEvent) event).getSecurityIsin().equals(security.getIsin()) &&
                            ((OpeningPriceEvent) event).getTradableQuantity() == 285
            ));
    }

    @Test
    void check_opening_price_if_an_order_gets_deleted(){
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);
        mockOrderHandler.handleDeleteOrder( new DeleteOrderRq( 2, security.getIsin(), Side.BUY, 1));
        ArgumentCaptor<OrderDeletedEvent> orderDeletedEventArgumentCaptor = ArgumentCaptor.forClass(OrderDeletedEvent.class);
        verify(mockEventPublisher).publish(orderDeletedEventArgumentCaptor.capture());
        OrderDeletedEvent outputEvent = orderDeletedEventArgumentCaptor.getValue();

        assertThat(orderBook.getBuyQueue().size()).isEqualTo(1);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(2);
        assertThat(security.getAuctionData().getBestOpeningPrice()).isEqualTo(0);
        assertThat(security.getAuctionData().getBestQuantity()).isEqualTo(0);
        ArgumentCaptor<OpeningPriceEvent> openingPriceEventArgumentCaptor = ArgumentCaptor.forClass(OpeningPriceEvent.class);
        verify(mockEventPublisher,times(2)).publish(openingPriceEventArgumentCaptor.capture());

        InOrder inOrder = inOrder(mockEventPublisher);
        inOrder.verify(mockEventPublisher).publish(new OpeningPriceEvent(security.getIsin(),15700,285));
        inOrder.verify(mockEventPublisher).publish(new OpeningPriceEvent(security.getIsin(),0,0));
        inOrder.verify(mockEventPublisher).publish(new OrderDeletedEvent(2,1));

    }


    @Test
    void check_trade_event_publish(){
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        ArgumentCaptor<OpeningPriceEvent> openingPriceEventArgumentCaptor = ArgumentCaptor.forClass
                (OpeningPriceEvent.class);
        ArgumentCaptor<TradeEvent> tradeEventArgumentCaptor = ArgumentCaptor.forClass(TradeEvent.class);
        verify(mockEventPublisher).publish(openingPriceEventArgumentCaptor.capture());
        verify(mockEventPublisher).publish(tradeEventArgumentCaptor.capture());
        OpeningPriceEvent outputEvent = openingPriceEventArgumentCaptor.getValue();
        TradeEvent tradeOutputEvent = tradeEventArgumentCaptor.getValue();
        assertThat(tradeOutputEvent.getPrice()).isEqualTo(15700);
        assertThat(tradeOutputEvent.getQuantity()).isEqualTo(285);
        assertThat(outputEvent.getSecurityIsin()).isEqualTo(security.getIsin());
        assertThat(tradeOutputEvent.getBuyId()).isEqualTo(1);
        assertThat(tradeOutputEvent.getSellId()).isEqualTo(7);
    }

    @Test
    void check_trades_with_different_opening_price_from_order_price_test() {
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        EnterOrderRq enterOrderRq2 = EnterOrderRq.createNewOrderRq(2, security.getIsin(),8,LocalDateTime.now(),
                Side.BUY, 300, 15820, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);
        mockOrderHandler.handleEnterOrder(enterOrderRq2);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        ArgumentCaptor<TradeEvent> tradeEventArgumentCaptor = ArgumentCaptor.forClass(TradeEvent.class);
        verify(mockEventPublisher, times(2)).publish(tradeEventArgumentCaptor.capture());
        InOrder inOrder = inOrder(mockEventPublisher);
        inOrder.verify(mockEventPublisher).publish(new TradeEvent(security.getIsin(), 15810, 285, 8, 7));
        inOrder.verify(mockEventPublisher).publish(new TradeEvent(security.getIsin(), 15810, 15, 8, 6));
    }

    @Test
    void check_credit_test(){
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        EnterOrderRq enterOrderRq2 = EnterOrderRq.createNewOrderRq(2, security.getIsin(),8,LocalDateTime.now(),
                Side.BUY, 300, 15820, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);
        mockOrderHandler.handleEnterOrder(enterOrderRq2);
        assertThat(broker2.getCredit()).isEqualTo(900_000_000);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getBroker().getBrokerId()).isEqualTo(2);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(broker2.getCredit()).isEqualTo(900_000_000 + 15810 * 300);
        assertThat(broker.getCredit()).isEqualTo(100_000_000 - 15810 * 300);

    }

    @Test
    void check_positions_test(){
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        EnterOrderRq enterOrderRq2 = EnterOrderRq.createNewOrderRq(2, security.getIsin(),8,LocalDateTime.now(),
                Side.BUY, 300, 15820, broker.getBrokerId(), shareholder2.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);
        mockOrderHandler.handleEnterOrder(enterOrderRq2);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(broker2.getCredit()).isEqualTo(900_000_000 + 15810 * 300);
        assertThat(broker.getCredit()).isEqualTo(100_000_000 - 15810 * 300);
        assertThat(shareholder2.getPositions().get(security)).isEqualTo(100_000 + 300);
        assertThat(shareholder.getPositions().get(security)).isEqualTo(100_000 - 300);

    }

    @Test
    void check_if_stop_limit_activates_and_does_not_trade_on_first_Auction(){
        security.setLastTradePrice(15750);
        EnterOrderRq enterOrderRq2 = EnterOrderRq.createNewStopOrderRequest(2, security.getIsin(),8,LocalDateTime.now(),
                Side.SELL, 15, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 15700);
        mockOrderHandler.handleEnterOrder(enterOrderRq2);
        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);
        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(orderBook.getBuyQueue().size()).isEqualTo(2);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(2);
        ArgumentCaptor<OrderActivatedEvent> orderActivatedEventArgumentCaptor = ArgumentCaptor.forClass(OrderActivatedEvent.class);
        verify(mockEventPublisher).publish(orderActivatedEventArgumentCaptor.capture());
        OrderActivatedEvent orderActivatedEvent = orderActivatedEventArgumentCaptor.getValue();
        assertThat(orderActivatedEvent.getOrderId()).isEqualTo(8);
        assertThat(orderActivatedEvent.getRqId()).isEqualTo(2);

    }


    @Test
    void check_trades_after_changing_state_back_to_continuous()
    {
        security.setLastTradePrice(15750);
        EnterOrderRq enterOrderRq2 = EnterOrderRq.createNewStopOrderRequest(2, security.getIsin(),8,LocalDateTime.now(),
                Side.SELL, 15, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 15700);
        mockOrderHandler.handleEnterOrder(enterOrderRq2);

        MatchingStateRq matchingStateRq = CreateNewMatchingStateRq(security.getIsin(), MatcherState.AUCTION);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq);
        assertThat(security.getState()).isEqualTo(MatcherState.AUCTION);

        EnterOrderRq enterOrderRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(),7,LocalDateTime.now(),
                Side.SELL, 285, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0);
        mockOrderHandler.handleEnterOrder(enterOrderRq);

        assertThat(orderBook.getBuyQueue().size()).isEqualTo(2);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(2);

        MatchingStateRq matchingStateRq2 = CreateNewMatchingStateRq(security.getIsin(), MatcherState.CONTINUOUS);
        mockMatcherHandler.handleMatchStateRq(matchingStateRq2);
        assertThat(security.getState()).isEqualTo(MatcherState.CONTINUOUS);
        assertThat(orderBook.getBuyQueue().size()).isEqualTo(2);
        assertThat(orderBook.getSellQueue().size()).isEqualTo(1);

        assertThat(orderBook.getBuyQueue().getLast().getOrderId()).isEqualTo(2);
        ArgumentCaptor<OrderExecutedEvent> orderExecutedEventArgumentCaptor = ArgumentCaptor.forClass(OrderExecutedEvent.class);
        verify(mockEventPublisher).publish(orderExecutedEventArgumentCaptor.capture());
        OrderExecutedEvent orderExecutedEvent = orderExecutedEventArgumentCaptor.getValue();
        assertThat(orderExecutedEvent.getOrderId()).isEqualTo(1);
        assertThat(orderExecutedEvent.getTrades().get(0).buyOrderId()).isEqualTo(1);
        assertThat(orderExecutedEvent.getTrades().get(0).sellOrderId()).isEqualTo(8);
        assertThat(orderExecutedEvent.getTrades().get(0).quantity()).isEqualTo(15);
        assertThat(orderExecutedEvent.getTrades().get(0).price()).isEqualTo(15700);

        ArgumentCaptor<OrderActivatedEvent> orderActivatedEventArgumentCaptor = ArgumentCaptor.forClass(OrderActivatedEvent.class);
        verify(mockEventPublisher).publish(orderActivatedEventArgumentCaptor.capture());
        OrderActivatedEvent orderActivatedEvent = orderActivatedEventArgumentCaptor.getValue();
        assertThat(orderActivatedEvent.getOrderId()).isEqualTo(8);
        assertThat(orderActivatedEvent.getRqId()).isEqualTo(2);

    }
}


