package ir.ut.se.tinyme.messaging.event;

import ir.ut.se.tinyme.messaging.request.MatcherState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class SecurityStateChangedEvent extends Event {
    private String securityIsin;
    private MatcherState matcherState;
}
