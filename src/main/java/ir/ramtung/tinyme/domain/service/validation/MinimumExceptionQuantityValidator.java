package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;

import java.util.List;

public class MinimumExceptionQuantityValidator extends BaseValidator{
    @Override
    public void validate(ValidationArg validationArg, List<String> errorList)
    {
        if (validationArg.enterOrderRq().getMinimumExecutionQuantity() < 0)
            errorList.add(Message.MINIMUM_EXCEPTION_QUANTITY_CANNOT_BE_NEGATIVE);
    }
}
