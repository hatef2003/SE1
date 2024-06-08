package ir.ramtung.tinyme.domain.service.validation;

import java.util.List;

import ir.ramtung.tinyme.messaging.Message;

public class OrderAttributesValidator extends BaseValidator {
    @Override
    public void validate(ValidationArg validationArg, List<String> errorList) {
        if (validationArg.enterOrderRq().getOrderId() <= 0)
            errorList.add(Message.INVALID_ORDER_ID);
        if (validationArg.enterOrderRq().getQuantity() <= 0)
            errorList.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (validationArg.enterOrderRq().getPrice() <= 0)
            errorList.add(Message.ORDER_PRICE_NOT_POSITIVE);
        if(next != null)
            next.validate(validationArg, errorList);
    }

}
