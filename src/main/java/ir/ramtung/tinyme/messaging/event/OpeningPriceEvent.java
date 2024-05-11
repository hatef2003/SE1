package ir.ramtung.tinyme.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Data
//@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class OpeningPriceEvent extends Event {
    LocalDateTime time;
    String securityIsin;
    int openingPrice;
    int tradableQuantity;
    public OpeningPriceEvent(String _securityIsin, int _openingPrice, int _tradableQuantity)
    {
        time = LocalDateTime.now();
        securityIsin = _securityIsin;
        openingPrice = _openingPrice;
        tradableQuantity = _tradableQuantity;
    }
    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof OpeningPriceEvent))
            return false;
        else
            return Objects.equals(((OpeningPriceEvent) other).getSecurityIsin(), securityIsin) &&
                    ((OpeningPriceEvent) other).getOpeningPrice() == openingPrice &&
                    ((OpeningPriceEvent) other).getTradableQuantity() == tradableQuantity;
    }
}
