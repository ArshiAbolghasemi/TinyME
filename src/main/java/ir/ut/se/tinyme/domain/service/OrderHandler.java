package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.event.*;
import ir.ut.se.tinyme.messaging.exception.InvalidRequestException;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.TradeDTO;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.messaging.request.MatcherState;
import ir.ut.se.tinyme.messaging.request.OrderEntryType;
import ir.ut.se.tinyme.repository.BrokerRepository;
import ir.ut.se.tinyme.repository.SecurityRepository;
import ir.ut.se.tinyme.repository.ShareholderRepository;
import ir.ut.se.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Matcher matcher;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
    }

    public void enterOrderPublishEvent(EnterOrderRq enterOrderRq, LinkedList<MatchResult> results) {
        List<Event> events = new ArrayList<Event>();

        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            events.add(new OrderAcceptedEvent(enterOrderRq.getRequestId(), 
                  enterOrderRq.getOrderId()));
        else
            events.add(new OrderUpdatedEvent(enterOrderRq.getRequestId(), 
                  enterOrderRq.getOrderId()));

        for (MatchResult matchResult : results) {
          events.addAll(matchResult.events());
        }

        eventPublisher.publishMany(events);
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            validateEnterOrderRq(enterOrderRq);

            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
            Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());

            LinkedList<MatchResult> results;
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                results = security.newOrder(enterOrderRq, broker, shareholder, matcher);
            else
                results = security.updateOrder(enterOrderRq, matcher);

            enterOrderPublishEvent(enterOrderRq, results);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void deleteOrderPublishEvent(MatchResult matchResult, DeleteOrderRq deleteOrderRq){
        List<Event> events = new ArrayList<Event>();
        if (matchResult != null) {
          events.addAll(matchResult.events());
        }
        events.add(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
        eventPublisher.publishMany(events);
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            MatchResult result = security.deleteOrder(deleteOrderRq);
            deleteOrderPublishEvent(result, deleteOrderRq);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    private void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        }
        if(enterOrderRq.getPeakSize() != 0 && enterOrderRq.getMinimumExecutionQuantity() != 0)
            errors.add(Message.MEQ_ORDERS_CANT_BE_PEAK_ORDERS);
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
        if (!this.isValidMinimumExecutionQuantityRange(enterOrderRq))
            errors.add(Message.INVALID_MINIMUM_EXECUTION_QUANTITY_RANGE);
        if (this.validateMEQAndStopLimitNewOrderCondition(enterOrderRq) && (enterOrderRq.getRequestType() ==  OrderEntryType.NEW_ORDER))
            errors.add(Message.CAN_NOT_INITIALIZE_MEQ_OR_STOP_LIMIT_ORDERS_ON_AUCTION_MODE);
        checkTheStopLimitConditions(enterOrderRq, errors);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private boolean validateMEQAndStopLimitNewOrderCondition(EnterOrderRq enterOrderRq){
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if(security == null) {
          return false;
        }
        return security.getState() == MatcherState.AUCTION && (enterOrderRq.getMinimumExecutionQuantity() != 0
                || enterOrderRq.getStopPrice() != 0);
    }

    private void checkTheStopLimitConditions(EnterOrderRq enterOrderRq,List<String> errors){
        int stopPrice = enterOrderRq.getStopPrice();
        if ( stopPrice < 0)
            errors.add(Message.INVALID_STOP_PRICE_VALUE);
        if (stopPrice != 0 && enterOrderRq.getPeakSize() != 0){
            errors.add(Message.ICEBERG_ORDERS_CANT_BE_STOP_PRICE_ORDERS);
        }
        if (stopPrice != 0 && enterOrderRq.getMinimumExecutionQuantity() != 0){
           errors.add(Message.MEQ_ORDERS_CANT_BE_STOP_PRICE_ORDERS);
        }
    }

    private boolean isValidMinimumExecutionQuantityRange(EnterOrderRq enterOrderRq) {
        return (enterOrderRq.getMinimumExecutionQuantity() >= 0 &&
                enterOrderRq.getMinimumExecutionQuantity() <= enterOrderRq.getQuantity());
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }
}
