package ir.ramtung.tinyme.messaging.request;

public class ChangeMatchingStateRq {
    String securityIsin;
    MatchingState targetState ;
    public ChangeMatchingStateRq(String securityIsin, MatchingState targetState)
    {
        this.securityIsin= securityIsin;
        this.targetState = targetState;
    }
    
}
