package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.messaging.EventPublisher;
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
    EventPublisher eventPublisher;

   public MatcherHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository,
                         ShareholderRepository shareholderRepository, EventPublisher eventPublisher) {
       this.securityRepository = securityRepository;
       this.brokerRepository = brokerRepository;
       this.shareholderRepository = shareholderRepository;
       this.eventPublisher = eventPublisher;
   }

    public void handleMatchStateRq(MatchingStateRq matchingStateRq){

    }

}
