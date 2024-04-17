package ir.ramtung.tinyme.messaging.event;

import java.util.ArrayList;
import java.util.List;

import ir.ramtung.tinyme.messaging.TradeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class OrderActivatedEvent extends Event {
    private long requestId;
    private long orderId;
    private List<TradeDTO> trades;
}