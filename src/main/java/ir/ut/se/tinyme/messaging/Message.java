package ir.ut.se.tinyme.messaging;

public class Message {
    public static final String INVALID_ORDER_ID = "Invalid order ID";
    public static final String ORDER_QUANTITY_NOT_POSITIVE = "Order quantity is not-positive";
    public static final String ORDER_PRICE_NOT_POSITIVE = "Order price is not-positive";
    public static final String UNKNOWN_SECURITY_ISIN = "Unknown security ISIN";
    public static final String ORDER_ID_NOT_FOUND = "Order ID not found in the order book";
    public static final String INVALID_PEAK_SIZE = "Iceberg order peak size is out of range";
    public static final String CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER = "Cannot specify peak size for a non-iceberg order";
    public static final String UNKNOWN_BROKER_ID = "Unknown broker ID";
    public static final String UNKNOWN_SHAREHOLDER_ID = "Unknown shareholder ID";
    public static final String BUYER_HAS_NOT_ENOUGH_CREDIT = "Buyer has not enough credit";
    public static final String QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE = "Quantity is not a multiple of security lot size";
    public static final String PRICE_NOT_MULTIPLE_OF_TICK_SIZE = "Price is not a multiple of security tick size";
    public static final String SELLER_HAS_NOT_ENOUGH_POSITIONS = "Seller has not enough positions";
    public static final String INVALID_MINIMUM_EXECUTION_QUANTITY_RANGE = "Minimum Execution Quantity is out of range";
    public static final String COULD_NOT_UPDATE_MEQ = "Could Not Update MEQ";
    public static final String INVALID_STOP_PRICE_VALUE = "StopPrice is invalid";
    public static final String ICEBERG_ORDERS_CANT_BE_STOP_PRICE_ORDERS = "Iceberg orders can't be stop price orders";
    public static final String MEQ_ORDERS_CANT_BE_STOP_PRICE_ORDERS = "MEQ orders can't be stop price orders";
    public static final String COULD_NOT_UPDATE_STOP_LIMIT_PRICE_FOR_NON_LIMIT_PRICE_ORDER_OR_NON_ACTIVE_STOPLIMIT_ORDER = "Could not update stop limit price for non-limit price or non active stoplimit order";
    public static final String MEQ_MIN_TRADE_NOT_MET = "MEQ minimum trade not met";
    public static final String CAN_NOT_INITIALIZE_MEQ_OR_STOP_LIMIT_ORDERS_ON_AUCTION_MODE = "can not initialize MEQ or stop limit orders on Auction mode";

//    public static final String COULD_NOT_UPDATE_STOP_ORDER_LIMIT_ORDER_THAT_IS_NOT_IN_ACTIVE = "Could not update stop order limit that is not inactive";
}
