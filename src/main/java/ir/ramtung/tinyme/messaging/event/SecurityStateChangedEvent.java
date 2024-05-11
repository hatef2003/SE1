package ir.ramtung.tinyme.messaging.event;

import ir.ramtung.tinyme.messaging.request.MatchingState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Objects;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class SecurityStateChangedEvent extends Event{
        private LocalDateTime time;
        private String securityIsin;
        private MatchingState state;
        public SecurityStateChangedEvent(String _securityIsin, MatchingState _state)
        {
                time = LocalDateTime.now();
                securityIsin = _securityIsin;
                state = _state;
        }
        @Override
        public boolean equals(Object other)
        {
                if (!(other instanceof SecurityStateChangedEvent))
                        return false;
                else
                        return Objects.equals(((SecurityStateChangedEvent) other).getSecurityIsin(), securityIsin) &&
                                ((SecurityStateChangedEvent) other).getState() == state;
        }
}
