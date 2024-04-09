package ir.ut.se.tinyme.messaging;

import ir.ut.se.tinyme.domain.entity.Trade;

public record TradeDTO(
    String securityIsin,
    int price,
    int quantity,
    long buyOrderId,
    long sellOrderId) {

    public TradeDTO(Trade trade) {
        this(trade.getSecurity().getIsin(), trade.getPrice(), trade.getQuantity(), trade.getBuy().getOrderId(), trade.getSell().getOrderId());
    }
}
