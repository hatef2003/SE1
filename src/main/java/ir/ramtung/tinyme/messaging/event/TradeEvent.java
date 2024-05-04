package ir.ramtung.tinyme.messaging.event;

import ir.ramtung.tinyme.domain.entity.Trade;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class TradeEvent extends Event {
    String securityIsin;
    int price;
    int quantity;
    long buyId;
    long sellId;

    public TradeEvent(Trade trade) {
        securityIsin = trade.getSecurity().getIsin();
        price = trade.getPrice();
        quantity = trade.getQuantity();
        buyId = trade.getBuy().getOrderId();
        sellId = trade.getSell().getOrderId();
    }
}
