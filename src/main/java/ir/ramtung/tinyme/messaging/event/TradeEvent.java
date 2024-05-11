package ir.ramtung.tinyme.messaging.event;

import ir.ramtung.tinyme.domain.entity.Trade;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;
import java.util.Objects;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class TradeEvent extends Event {
    LocalDateTime time;
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
        time = LocalDateTime.now();
    }

    public TradeEvent(String _securityIsin, int _price, int _quantity, long _buyId, long _sellId)
    {
        time = LocalDateTime.now();
        securityIsin = _securityIsin;
        price = _price;
        quantity = _quantity;
        buyId = _buyId;
        sellId = _sellId;
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof TradeEvent))
            return false;
        else
            return Objects.equals(((TradeEvent) other).getSecurityIsin(), securityIsin) &&
                    ((TradeEvent) other).getPrice() == price &&
                    ((TradeEvent) other).getQuantity() == quantity &&
                    ((TradeEvent) other).getBuyId() == buyId &&
                    ((TradeEvent) other).getSellId() == sellId;
    }
}
