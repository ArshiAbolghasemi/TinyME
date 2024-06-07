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
import java.util.ArrayList;
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

   private void publishEvents(List<MatchResult> matchResults) {
      List<Event> events = new ArrayList<Event>();
      for (MatchResult matchResult : matchResults) {
        events.addAll(matchResult.events());
      }

      eventPublisher.publishMany(events);
   }

    public void handleMatchStateRq(MatchingStateRq matchingStateRq){
        Security security = securityRepository.findSecurityByIsin(matchingStateRq.getSecurityIsin());
        MatcherState state = security.getState();
        List<MatchResult> matchResults = new ArrayList<MatchResult>();

        if (state == MatcherState.AUCTION){
            security.FillSelectedOrderList();
            LinkedList<MatchResult> results = matcher.matchOrderBook(security);
            matchResults.addAll(results);
        }
        security.setState(matchingStateRq.getState());
        if (security.getState() == MatcherState.CONTINUOUS && 
            state == MatcherState.AUCTION){
            security.FillSelectedOrderList();
            LinkedList<MatchResult> results = matcher.matchOrderBook(security);
            matchResults.addAll(results);
        }

        publishEvents(matchResults);

        eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), 
              security.getState()));
    }

}
