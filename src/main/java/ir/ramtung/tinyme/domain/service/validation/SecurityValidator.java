package ir.ramtung.tinyme.domain.service.validation;

import java.util.List;

import ir.ramtung.tinyme.messaging.Message;

public class SecurityValidator extends BaseValidator {
    @Override
    public void validate(ValidationArg validationArg, List<String> errorList) {
        if (validationArg.security() != null) {
            if (validationArg.enterOrderRq().getQuantity() % validationArg.security().getLotSize() != 0)
                errorList.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (validationArg.enterOrderRq().getPrice() % validationArg.security().getTickSize() != 0)
                errorList.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        } else
            errorList.add(Message.UNKNOWN_SECURITY_ISIN);
        if (next != null) {
            next.validate(validationArg, errorList);
        }
    }

}
