package ir.ramtung.tinyme.domain.service.validation;

import java.util.List;

import ir.ramtung.tinyme.messaging.Message;

public class StopLimitValidator extends BaseValidator {
    @Override
    public void validate(ValidationArg validationArg, List<String> errorList) {
        if(validationArg.enterOrderRq().getStopLimit() != 0) {
            if (validationArg.enterOrderRq().getPeakSize() != 0)
                errorList.add(Message.STOP_LIMIT_ORDER_IS_ICEBERG);
            if (validationArg.enterOrderRq().getMinimumExecutionQuantity() > 0)
                errorList.add(Message.STOP_LIMIT_ORDER_HAS_MINIMUM_EXECUTION_QUANTITY);
        }

        if (validationArg.enterOrderRq().getStopLimit() < 0)
            errorList.add(Message.INVALID_STOP_LIMIT);

        if(next != null)
            next.validate(validationArg, errorList);
    }

}
