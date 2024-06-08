package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.domain.entity.IcebergOrder;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;

import java.util.List;

public class UpdateValidator extends BaseValidator {
    @Override
    public void validate(ValidationArg validationArg, List<String> errorList) {

        if (validationArg.enterOrderRq().getRequestType() == OrderEntryType.UPDATE_ORDER && errorList.isEmpty()) {
            Order order = validationArg.security().getOrderFromRequest(validationArg.enterOrderRq());
            if (order == null)
                errorList.add(Message.ORDER_ID_NOT_FOUND);
            if (!(order instanceof StopLimitOrder) && validationArg.enterOrderRq().getStopLimit() != 0)
                    errorList.add(Message.ACTIVE_ORDER_CANT_HAVE_STOP_LIMIT);
            if (order instanceof IcebergOrder) {
                if (validationArg.enterOrderRq().getPeakSize() == 0)
                    errorList.add(Message.INVALID_PEAK_SIZE);
            }
            else if (validationArg.enterOrderRq().getPeakSize() != 0)
                errorList.add(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        }
        if(next != null)
            next.validate(validationArg, errorList);
    }

}
