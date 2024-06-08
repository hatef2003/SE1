package ir.ramtung.tinyme.domain.service.validation;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.Getter;
public record ValidationArg(EnterOrderRq enterOrderRq , Shareholder shareholder , Broker broker  ,Security security) {}