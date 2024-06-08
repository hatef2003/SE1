package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.IcebergOrder;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.OrderStatus;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;

public class OrderFactory {
    private  SecurityRepository securityRepository;
    private  ShareholderRepository shareholderRepository;
    private BrokerRepository brokerRepository; 
    public OrderFactory(  SecurityRepository securityRepository,  ShareholderRepository shareholderRepository,BrokerRepository brokerRepository)
    {
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.securityRepository = securityRepository;
    }
    public Order createOrder(EnterOrderRq enterOrderRq)
    {
        if (enterOrderRq.getPeakSize() != 0)
            return new IcebergOrder(enterOrderRq.getOrderId(), securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin()), enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), brokerRepository.findBrokerById(enterOrderRq.getBrokerId()), shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()),
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(),
                    enterOrderRq.getMinimumExecutionQuantity());
        else if (enterOrderRq.getStopLimit() != 0) {
            return new StopLimitOrder(enterOrderRq.getOrderId(), securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin()), enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), brokerRepository.findBrokerById(enterOrderRq.getBrokerId()), shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()),
                    enterOrderRq.getEntryTime(), OrderStatus.NEW, enterOrderRq.getStopLimit());
        } else
            return new Order(enterOrderRq.getOrderId(), securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin()), enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), brokerRepository.findBrokerById(enterOrderRq.getBrokerId()), shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()),
                    enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity());

    }
}
