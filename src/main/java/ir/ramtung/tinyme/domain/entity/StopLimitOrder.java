package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StopLimitOrder extends Order{
    private int stopLimit;

    public boolean isActive(int lastTradePrice)
    {
        if (lastTradePrice == -1)
            return false;

        if (this.getSide() == Side.BUY)
            return lastTradePrice >= stopLimit;
        else
            return lastTradePrice <= stopLimit;
    }
    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder,LocalDateTime entryTime, OrderStatus status , int stopLimit) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, 0);
        this.stopLimit = stopLimit;
    }
    public void restoreBrokerCredit()
    {
        broker.increaseCreditBy((long) quantity * price);
    }
    @Override
    public Order snapshot() {
        return new StopLimitOrder(orderId, security, side, quantity, price, broker, shareholder, entryTime, OrderStatus.SNAPSHOT, stopLimit);
    }
    @Override
    public Order snapshotWithQuantity(int newQuantity) {
        return new StopLimitOrder(orderId, security, side, newQuantity, price, broker, shareholder, entryTime, OrderStatus.SNAPSHOT, stopLimit);
    }
    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        quantity = updateOrderRq.getQuantity();
        price = updateOrderRq.getPrice();
        stopLimit = updateOrderRq.getStopLimit();
    }
}