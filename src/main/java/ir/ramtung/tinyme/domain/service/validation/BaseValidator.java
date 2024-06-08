package ir.ramtung.tinyme.domain.service.validation;

import lombok.Setter;

import java.util.List;

@Setter
public abstract class  BaseValidator {
    protected BaseValidator next;
    public abstract void validate(ValidationArg validationArg, List<String> errorList);

}
