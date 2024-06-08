package ir.ramtung.tinyme.domain.service.validation;

import java.util.List;

import ir.ramtung.tinyme.messaging.Message;


public class BrokerValidator extends  BaseValidator{
    @Override
    public void validate( ValidationArg validationArg, List<String> errorList)
    {
        if (validationArg.broker() == null)
            errorList.add(Message.UNKNOWN_BROKER_ID);
        if (next != null)
            next.validate(validationArg, errorList);
    }

}

