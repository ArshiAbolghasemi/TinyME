package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.domain.entity.MatchResult;
import ir.ut.se.tinyme.domain.entity.MatchingOutcome;
import ir.ut.se.tinyme.domain.entity.Security;
import ir.ut.se.tinyme.domain.entity.Trade;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.TradeDTO;
import ir.ut.se.tinyme.messaging.event.*;
import ir.ut.se.tinyme.messaging.request.MatcherState;
import ir.ut.se.tinyme.messaging.request.MatchingStateRq;
import ir.ut.se.tinyme.repository.BrokerRepository;
import ir.ut.se.tinyme.repository.SecurityRepository;
import ir.ut.se.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MatcherHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Matcher matcher;

   public MatcherHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository,
                         ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
       this.securityRepository = securityRepository;
       this.brokerRepository = brokerRepository;
       this.shareholderRepository = shareholderRepository;
       this.eventPublisher = eventPublisher;
       this.matcher = matcher;
   }

   private void publishEvents(LinkedList<MatchResult> matchResults, MatcherState state){
       for (MatchResult matchResult : matchResults) {
           if (matchResult.outcome() == MatchingOutcome.NEW_OPEN_PRICE_CALCULATED) {
               eventPublisher.publish(new OpeningPriceEvent(matchResult.security().getIsin()
                       ,matchResult.security().getAuctionData().getBestOpeningPrice(), matchResult.security().getAuctionData().getBestQuantity()));
           }
           if (matchResult.outcome() == MatchingOutcome.STOP_LIMIT_ORDER_ACTIVATED) {
               eventPublisher.publish(new OrderActivatedEvent(matchResult.remainder().getRqId(),matchResult.remainder().getOrderId()));
           }
           if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
               eventPublisher.publish(new OrderRejectedEvent(matchResult.remainder().getRqId(),
                       matchResult.remainder().getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
               continue;
           }
           if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
               eventPublisher.publish(new OrderRejectedEvent(matchResult.remainder().getRqId(),
                       matchResult.remainder().getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
               continue;
           }
           if (!matchResult.trades().isEmpty()) {
               if(state == MatcherState.AUCTION){
                   for(Trade trade : matchResult.trades()) {
                       eventPublisher.publish(new TradeEvent(trade.getSecurity().getIsin(), trade.getPrice(),
                               trade.getQuantity(), trade.getBuy().getOrderId(), trade.getSell().getOrderId()));
                   }
               }
               else if (state == MatcherState.CONTINUOUS ){
                   eventPublisher.publish(new OrderExecutedEvent(matchResult.remainder().getRqId(), matchResult.remainder().getOrderId(),
                           matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
               }
           }
       }
   }

    public void handleMatchStateRq(MatchingStateRq matchingStateRq){
        Security security = securityRepository.findSecurityByIsin(matchingStateRq.getSecurityIsin());
        MatcherState state = security.getState();
        if ( state == MatcherState.AUCTION){
            security.FillSelectedOrderList();
            LinkedList<MatchResult> matchResults = matcher.matchOrderBook(security);
            publishEvents(matchResults, security.getState());
        }
        security.setState(matchingStateRq.getState());
        if (security.getState() == MatcherState.CONTINUOUS && state == MatcherState.AUCTION){
            security.FillSelectedOrderList();
            LinkedList<MatchResult> matchResults = matcher.matchOrderBook(security);
            publishEvents(matchResults, security.getState());
        }
        eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), security.getState()));
    }

}
