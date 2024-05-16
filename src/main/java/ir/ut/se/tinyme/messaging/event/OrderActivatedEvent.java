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
public class OrderActivatedEvent extends Event{
    private long rqId;
    private long orderId;
}
