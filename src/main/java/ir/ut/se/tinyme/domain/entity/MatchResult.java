package ir.ut.se.tinyme.domain.entity;

import ir.ut.se.tinyme.messaging.event.*;
import ir.ut.se.tinyme.messaging.Message;
import ir.ut.se.tinyme.messaging.request.MatcherState;
import ir.ut.se.tinyme.messaging.TradeDTO;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

public final class MatchResult {
    private final MatchingOutcome outcome;
    private final Order remainder;
    private final LinkedList<Trade> trades;
    private final Security security;

    public static MatchResult executed(Order remainder, List<Trade> trades) {
        return new MatchResult(MatchingOutcome.EXECUTED, remainder, new LinkedList<>(trades), null);
    }

    public static MatchResult notEnoughCredit() {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_CREDIT, null, new LinkedList<>(), null);
    }

    public static MatchResult notEnoughPositions() {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_POSITIONS, null, new LinkedList<>(), null);
    }

    public static MatchResult minimumExecutionQuantityNotMet() {
        return new MatchResult(MatchingOutcome.MINIMUM_EXECUTION_QUANTITY_NOT_MET, null, new LinkedList<>(), null);
    }

    public static MatchResult stopLimitOrderActivated(Order remainder, List<Trade> trades) {
        return new MatchResult(MatchingOutcome.STOP_LIMIT_ORDER_ACTIVATED, remainder, new LinkedList<>(trades), null);
    }

    public static MatchResult noMatchingOccurred(){
        return new MatchResult(MatchingOutcome.NO_MATCHING_OCCURRED, null, new LinkedList<>(), null);
    }

    public static MatchResult newOpenPriceCalculated(Security security){
        return new MatchResult(MatchingOutcome.NEW_OPEN_PRICE_CALCULATED, null, new LinkedList<>(),security);
    }

    private MatchResult(MatchingOutcome outcome, Order remainder, LinkedList<Trade> trades, Security security) {
        this.outcome = outcome;
        this.remainder = remainder;
        this.trades = trades;
        this.security = security;
    }

    public MatchingOutcome outcome() {
        return outcome;
    }
    public Order remainder() {
        return remainder;
    }

    public LinkedList<Trade> trades() {
        return trades;
    }

    public Security security() {
        return security;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MatchResult) obj;
        return Objects.equals(this.remainder, that.remainder) &&
                Objects.equals(this.trades, that.trades);
    }

    @Override
    public int hashCode() {
        return Objects.hash(remainder, trades);
    }

    @Override
    public String toString() {
        return "MatchResult[" +
                "remainder=" + remainder + ", " +
                "trades=" + trades + ']';
    }

    public List<Event> events() {
      List<Event> events = new ArrayList<Event>();

      if (!this.trades().isEmpty()) {
        if(this.security.getState() == MatcherState.AUCTION){
          for(Trade trade : this.trades()) {
            events.add(new TradeEvent(trade.getSecurity().getIsin(),
                  trade.getPrice(), trade.getQuantity(),
                  trade.getBuy().getOrderId(), trade.getSell().getOrderId()));
          }
        } else if (this.security.getState() == MatcherState.CONTINUOUS ){ 
          events.add(new OrderExecutedEvent(this.remainder().getRqId(), 
                this.remainder().getOrderId(),
                this.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
        }
      }

      if (this.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
          events.add(new OrderRejectedEvent(this.remainder().getRqId(),
                this.remainder().getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
          
      } else if (this.outcome() == MatchingOutcome.STOP_LIMIT_ORDER_ACTIVATED) {
          events.add(new OrderRejectedEvent(this.remainder().getRqId(),
                this.remainder().getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
      } else if (this.outcome() == MatchingOutcome.NEW_OPEN_PRICE_CALCULATED) {
          events.add(new OpeningPriceEvent(this.security().getIsin(),
                this.security().getAuctionData().getBestOpeningPrice(),
                this.security().getAuctionData().getBestQuantity()));
      } else if (this.outcome() == MatchingOutcome.STOP_LIMIT_ORDER_ACTIVATED) {
          events.add(new OrderActivatedEvent(this.remainder().getRqId(),
                this.remainder().getOrderId()));
      } 

      return events;
    }

}
