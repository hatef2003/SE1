package ir.ramtung.tinyme.domain.service.validation;

import java.util.List;

public abstract class  BaseValidator {
    protected BaseValidator next;
    public void setNext(BaseValidator next)
    {
        this.next = next;
    }
    public abstract void validate(ValidationArg validationArg , List<String> errorList);

}
