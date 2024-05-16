package ir.ut.se.tinyme.messaging;

import ir.ut.se.tinyme.domain.service.MatcherHandler;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.domain.service.OrderHandler;
import ir.ut.se.tinyme.messaging.request.MatchingStateRq;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class RequestDispatcher {
    private final Logger log = Logger.getLogger(this.getClass().getName());
    private final OrderHandler orderHandler;
    private final MatcherHandler matcherHandler;

    public RequestDispatcher(OrderHandler orderHandler, MatcherHandler matcherHandler) {
        this.orderHandler = orderHandler;
        this.matcherHandler = matcherHandler;
    }

    @JmsListener(destination = "${requestQueue}", selector = "_type='ir.ramtung.tinyme.messaging.request.EnterOrderRq'")
    public void receiveEnterOrderRq(EnterOrderRq enterOrderRq) {
        log.info("Received message: " + enterOrderRq);
        orderHandler.handleEnterOrder(enterOrderRq);
    }

    @JmsListener(destination = "${requestQueue}", selector = "_type='ir.ramtung.tinyme.messaging.request.MatchingStateRq'")
    public void receiveChangeMatchingStateRq(MatchingStateRq matchingStateRq) {
        log.info("Received message: " + matchingStateRq);
        matcherHandler.handleMatchStateRq(matchingStateRq);
    }

    @JmsListener(destination = "${requestQueue}", selector = "_type='ir.ramtung.tinyme.messaging.request.DeleteOrderRq'")
    public void receiveDeleteOrderRq(DeleteOrderRq deleteOrderRq) {
        log.info("Received message: " + deleteOrderRq);
        orderHandler.handleDeleteOrder(deleteOrderRq);
    }
}
