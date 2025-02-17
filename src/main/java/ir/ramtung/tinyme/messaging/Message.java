package ir.ramtung.tinyme.messaging;

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
    public static final String TRADE_QUANTITY_LESS_THAN_MINIMUM = "Trade quantity is less than minimum execution quantity";
    public static final String STOP_LIMIT_ORDER_IS_ICEBERG = "Stop limit order can't be Iceberg order";
    public static final String STOP_LIMIT_ORDER_HAS_MINIMUM_EXECUTION_QUANTITY = "Stop limit orders can't have minimum execution quantity ";
    public static final String ACTIVE_ORDER_CANT_HAVE_STOP_LIMIT = "Active order cant have stop limit";
    public static final String INVALID_STOP_LIMIT = "Stop Limit must be positive";
    public static final String PEAK_SIZE_MUST_BE_LESS_THAN_TOTAL_QUANTITY = "Peak size must be less than total quantity";
    public static final String AUCTION_CANNOT_HANDLE_MINIMUM_EXECUTION_QUANTITY = "auction matcher cannot handle minimum execution quantity";
    public static final String AUCTION_CANNOT_HANDLE_STOP_LIMIT_ORDER = "Auction matcher cannot handle stop limit order";

    public static final String MINIMUM_EXCEPTION_QUANTITY_CANNOT_BE_NEGATIVE = "Minimum exception quantity cannot be negative";
}
