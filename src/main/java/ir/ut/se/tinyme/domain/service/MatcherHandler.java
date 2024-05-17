package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.domain.entity.MatchResult;
import ir.ut.se.tinyme.domain.entity.MatchingOutcome;
import ir.ut.se.tinyme.domain.entity.Security;
import ir.ut.se.tinyme.domain.entity.Trade;
import ir.ut.se.tinyme.messaging.EventPublisher;
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

   private void publishEvents(LinkedList<MatchResult> matchResults, MatchingStateRq matchingStateRq){
       for (MatchResult matchResult : matchResults) {
           if (matchResult.outcome() == MatchingOutcome.NEW_OPEN_PRICE_CALCULATED) {
               eventPublisher.publish(new OpeningPriceEvent(matchResult.security().getIsin()
                       ,matchResult.security().getAuctionData().getBestOpeningPrice(), matchResult.security().getAuctionData().getBestQuantity()));
           }
           if (matchResult.outcome() == MatchingOutcome.STOP_LIMIT_ORDER_ACTIVATED) {
               eventPublisher.publish(new OrderActivatedEvent(matchResult.remainder().getRqId(),matchResult.remainder().getOrderId()));
           }
           if (!matchResult.trades().isEmpty()) {
               for(Trade trade : matchResult.trades()){
                   eventPublisher.publish(new TradeEvent(trade.getSecurity().getIsin(), trade.getPrice(),
                           trade.getQuantity(), trade.getBuy().getOrderId(), trade.getSell().getOrderId()));
               }
           }
       }
   }

    public void handleMatchStateRq(MatchingStateRq matchingStateRq){
        Security security = securityRepository.findSecurityByIsin(matchingStateRq.getSecurityIsin());
        if (security.getState() == MatcherState.AUCTION){
            LinkedList<MatchResult> matchResults = matcher.auction(security);
            publishEvents(matchResults, matchingStateRq);
        }
        security.setState(matchingStateRq.getState());
        eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), security.getState()));
    }

}
