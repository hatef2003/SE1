package ir.ramtung.tinyme.messaging.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChangeMatchingStateRq {
    String securityIsin;
    MatchingState targetState;
    int requestId;
}
