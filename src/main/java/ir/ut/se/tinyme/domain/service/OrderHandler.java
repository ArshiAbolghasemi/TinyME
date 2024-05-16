package ir.ut.se.tinyme.domain.service;

import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.event.*;
import ir.ut.se.tinyme.messaging.exception.InvalidRequestException;
import ir.ut.se.tinyme.messaging.EventPublisher;
import ir.ut.se.tinyme.messaging.TradeDTO;
import ir.ut.se.tinyme.messaging.request.DeleteOrderRq;
import ir.ut.se.tinyme.messaging.request.EnterOrderRq;
import ir.ut.se.tinyme.messaging.request.OrderEntryType;
import ir.ut.se.tinyme.repository.BrokerRepository;
import ir.ut.se.tinyme.repository.SecurityRepository;
import ir.ut.se.tinyme.repository.ShareholderRepository;
import ir.ut.se.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
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

    public void publishEvent(EnterOrderRq enterOrderRq, LinkedList<MatchResult> results) {
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        else
            eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        for (MatchResult matchResult : results) {
            if (matchResult.outcome() == MatchingOutcome.MINIMUM_EXECUTION_QUANTITY_NOT_MET) {
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(),
                        enterOrderRq.getOrderId(), List.of(Message.MEQ_MIN_TRADE_NOT_MET)));
                continue;
            }
            if(matchResult.outcome() == MatchingOutcome.CANT_INITIALIZE_MEQ_OR_STOP_LIMIT_DURING_AUCTION_MODE){
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(),
                        enterOrderRq.getOrderId(), List.of(Message.CAN_NOT_INITIALIZE_MEQ_OR_STOP_LIMIT_ORDERS_ON_AUCTION_MODE)));
            }
            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(),
                        enterOrderRq.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
                continue;
            }
            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(),
                        enterOrderRq.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
                continue;
            }
            if (matchResult.outcome() == MatchingOutcome.STOP_LIMIT_ORDER_ACTIVATED) {
                eventPublisher.publish(new OrderActivatedEvent(matchResult.remainder().getOrderId()));
            }
            if (!matchResult.trades().isEmpty()) {
                eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), matchResult.remainder().getOrderId(),
                        matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
            }
        }
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

            publishEvent(enterOrderRq, results);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            security.deleteOrder(deleteOrderRq);
            eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
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
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
        if (!this.isValidMinimumExecutionQuantityRange(enterOrderRq))
            errors.add(Message.INVALID_MINIMUM_EXECUTION_QUANTITY_RANGE);

        checkTheStopLimitConditions(enterOrderRq, errors);

        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
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
