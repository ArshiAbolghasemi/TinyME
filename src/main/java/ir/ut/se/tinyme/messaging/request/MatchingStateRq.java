package ir.ut.se.tinyme.messaging.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MatchingStateRq {
    private String securityIsin;
    private MatcherState state;

    private MatchingStateRq(String securityIsin, MatcherState state){
        this.securityIsin = securityIsin;
        this.state = state;
    }

    public static MatchingStateRq CreateNewMatchingStateRq(String securityIsin, MatcherState state){
        return new MatchingStateRq(securityIsin, state);
    }
}
