package ir.ramtung.tinyme.domain.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@EqualsAndHashCode
@ToString
public class Trade {
    private Security security;
    private int price;
    private int quantity;
    private Order buy;
    private Order sell;

    public Trade(Security security, int price, int quantity, Order order1, Order order2) {
        this.security = security;
        this.price = price;
        this.quantity = quantity;
        Order snapshot1 = order1.snapshot();
        Order snapshot2 = order2.snapshot();
        if (order1.getSide() == Side.BUY) {
            buy = snapshot1;
            sell = snapshot2;
        } else {
            buy = snapshot2;
            sell = snapshot1;
        }
    }

    public int getQuantity()
    {
        return quantity;
    }

    public long getTradedValue() {
        return (long) price * quantity;
    }

    public void increaseSellersCredit() {
        sell.getBroker().increaseCreditBy(getTradedValue());
    }

    public void decreaseBuyersCredit() {
        buy.getBroker().decreaseCreditBy(getTradedValue());
    }

    public boolean buyerHasEnoughCredit() {
        return buy.getBroker().hasEnoughCredit(getTradedValue());
    }

    public void changeShareholderPositions() {
        sell.getShareholder().decPosition(security, quantity);
        buy.getShareholder().incPosition(security, quantity);
    }

}
