package ir.ramtung.tinyme.domain.service.validation;

import java.util.List;

import ir.ramtung.tinyme.messaging.Message;

public class IceBergValidator extends BaseValidator {
    @Override
    public void validate(ValidationArg validationArg, List<String> errorList) {
        if (!isPeakSizeValid(validationArg))
            errorList.add(Message.INVALID_PEAK_SIZE);
        if (validationArg.enterOrderRq().getPeakSize() != 0 && validationArg.enterOrderRq().getQuantity() < validationArg.enterOrderRq().getPeakSize())
            errorList.add(Message.PEAK_SIZE_MUST_BE_LESS_THAN_TOTAL_QUANTITY);
        if (next != null)
            next.validate(validationArg, errorList);
    }

    private static boolean isPeakSizeValid(ValidationArg validationArg) {
        return validationArg.enterOrderRq().getPeakSize() >= 0
                && validationArg.enterOrderRq().getPeakSize() < validationArg.enterOrderRq().getQuantity();
    }

}
