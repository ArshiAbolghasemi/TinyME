package ir.ut.se.tinyme.domain.entity;

public enum MatchingOutcome {
    EXECUTED,
    NOT_ENOUGH_CREDIT,
    NOT_ENOUGH_POSITIONS,
    MINIMUM_EXECUTION_QUANTITY_NOT_MET,
    STOP_LIMIT_ORDER_ACTIVATED,
    NO_MATCHING_OCCURRED
}
