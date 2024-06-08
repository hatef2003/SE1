package ir.ramtung.tinyme.domain.service.validation;

import java.util.List;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.MatchingState;

public class AuctionValidator extends BaseValidator {
    @Override
    public void validate(ValidationArg validationArg, List<String> errorList) {
           if (validationArg.security() != null) {
            MatchingState state = validationArg.security().getState();
            if (state == MatchingState.AUCTION && validationArg.enterOrderRq().getMinimumExecutionQuantity() != 0)
                errorList.add(Message.AUCTION_CANNOT_HANDLE_MINIMUM_EXECUTION_QUANTITY);
            if (state == MatchingState.AUCTION && validationArg.enterOrderRq().getStopLimit() != 0)
                errorList.add(Message.AUCTION_CANNOT_HANDLE_STOP_LIMIT_ORDER);
        }
        if(next!=null)
        {
            next.validate(validationArg, errorList);
        }
    }
}
