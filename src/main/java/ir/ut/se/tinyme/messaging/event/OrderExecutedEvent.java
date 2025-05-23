package ir.ut.se.tinyme.messaging.event;

import ir.ut.se.tinyme.messaging.TradeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class OrderExecutedEvent extends Event {
    private long requestId;
    private long orderId;
    private List<TradeDTO> trades;
}
