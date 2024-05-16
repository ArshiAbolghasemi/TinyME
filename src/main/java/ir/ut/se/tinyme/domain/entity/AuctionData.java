package ir.ut.se.tinyme.domain.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
public class AuctionData {
    @Setter
    @Builder.Default
    private int bestOpeningPrice = 0;
    @Setter
    @Builder.Default
    private int bestQuantity = 0;

}
