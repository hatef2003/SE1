package ir.ramtung.tinyme.domain.service.validation;

import java.util.List;

import ir.ramtung.tinyme.messaging.Message;

public class ShareholderValidator extends BaseValidator {
    @Override
    public void validate(ValidationArg validationArg, List<String> errorList) {
        if (validationArg.shareholder()==null)
        {
            errorList.add(Message.UNKNOWN_SHAREHOLDER_ID);
        }

        if(next != null)
        {
            next.validate(validationArg, errorList);
        }
    }

}
