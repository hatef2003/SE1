package ir.ramtung.tinyme.domain.service.validation;

import java.util.LinkedList;
import java.util.List;

import org.springframework.stereotype.Service;

import ir.ramtung.tinyme.domain.entity.Broker;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.Shareholder;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

@Service
public class Validation {
    private final BaseValidator validatorHead;

    public Validation() {
        BaseValidator orderAttributesValidator = new OrderAttributesValidator();
        BaseValidator securityValidator = new SecurityValidator();
        BaseValidator brokerValidator = new BrokerValidator();
        BaseValidator shareholderValidator = new ShareholderValidator();
        BaseValidator icebergValidator = new IceBergValidator();
        BaseValidator stopLimitValidator = new StopLimitValidator();
        BaseValidator auctionValidator = new AuctionValidator();
        BaseValidator minimumExceptionValidator = new MinimumExceptionQuantityValidator();
        BaseValidator updateValidator = new UpdateValidator();
        orderAttributesValidator.setNext(securityValidator);
        securityValidator.setNext(brokerValidator);
        brokerValidator.setNext(shareholderValidator);
        shareholderValidator.setNext(icebergValidator);
        icebergValidator.setNext(stopLimitValidator);
        stopLimitValidator.setNext(auctionValidator);
        auctionValidator.setNext(updateValidator);
        validatorHead = orderAttributesValidator;
    }

    public void validate(EnterOrderRq enterOrderRq, Security security, Broker broker, Shareholder shareholder)
            throws InvalidRequestException {
                List<String> errorList = new LinkedList<>();
                ValidationArg  validationArg  = new ValidationArg(enterOrderRq, shareholder, broker, security);
                validatorHead.validate(validationArg, errorList);
                if (! errorList.isEmpty())
                    throw new InvalidRequestException(errorList);
        }

}
