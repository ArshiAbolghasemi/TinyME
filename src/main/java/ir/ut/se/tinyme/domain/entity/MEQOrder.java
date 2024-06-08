package ir.ut.se.tinyme.domain.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
public class MEQOrder extends Order {
    @Builder.Default
    protected int minimumExecutionQuantity = 0;
}
