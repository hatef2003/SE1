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
    int stopLimit;
    public boolean isActive(int lastTradePrice)
    {
        if (lastTradePrice == -1 )
        {
            return false; 
        }
        if(this.getSide() == Side.BUY )
        {

            if(lastTradePrice >= stopLimit )
            {
                return true;
            }
            else
            {
                return false ;
            }
        }
        else 
        {
            if (lastTradePrice <= stopLimit)
            {
                return true;
            }
            else
            {
                return false;
            }
        }
    }
    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder,LocalDateTime entryTime, OrderStatus status , int stopLimit) {
        // not sure if order status should always be queued, check again
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, 0);
        this.stopLimit = stopLimit;
    }

    @Override
    public Order snapshot() {
        return new StopLimitOrder(orderId, security, side, quantity, price, broker, shareholder, entryTime, OrderStatus.SNAPSHOT, stopLimit);
    }

    @Override
    public Order snapshotWithQuantity(int newQuantity) {
        return new StopLimitOrder(orderId, security, side, newQuantity, price, broker, shareholder, entryTime, OrderStatus.SNAPSHOT, stopLimit);
    }

}