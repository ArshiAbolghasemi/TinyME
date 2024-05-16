package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.domain.entity.Security;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.event.Event;
import ir.ut.se.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ut.se.tinyme.messaging.request.MatcherState;
import ir.ut.se.tinyme.messaging.request.MatchingStateRq;
import ir.ut.se.tinyme.repository.BrokerRepository;
import ir.ut.se.tinyme.repository.SecurityRepository;
import ir.ut.se.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class MatcherHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher publisher;
    Matcher matcher;

   public MatcherHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository,
                         ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
       this.securityRepository = securityRepository;
       this.brokerRepository = brokerRepository;
       this.shareholderRepository = shareholderRepository;
       this.publisher = eventPublisher;
       this.matcher = matcher;
   }

    public void handleMatchStateRq(MatchingStateRq matchingStateRq){
        Security security = securityRepository.findSecurityByIsin(matchingStateRq.getSecurityIsin());
        if (security.getState() == MatcherState.AUCTION){
            matcher.auction();
        }
        security.setMatcherState(matchingStateRq.getState());
        publisher.publish(new SecurityStateChangedEvent(security.getIsin(), security.getState()));
    }

}
