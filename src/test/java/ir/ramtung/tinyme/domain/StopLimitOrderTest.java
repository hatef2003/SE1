package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.SearchOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.*;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


@SpringBootTest
@Import(MockedJMSTestConfig.class)
public class StopLimitOrderTest {
    @Autowired
    OrderHandler orderHandler;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    private Security security;
    private Shareholder shareholder;
    private Broker broker1;
    private Broker broker2;
    private Broker broker3;
    @BeforeEach
    void setup()
    {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000_000);
        shareholderRepository.addShareholder(shareholder);

        broker1 = Broker.builder().brokerId(1).build();
        broker2 = Broker.builder().brokerId(2).build();
        broker3 = Broker.builder().brokerId(2).build();
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);
        brokerRepository.addBroker(broker3);
    }
    @Test
    void new_buy_stop_limit_order_add()
    {
        broker1.increaseCreditBy(1000 * 50);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,100,50,1,shareholder.getShareholderId(),0,0,100);
        orderHandler.handleEnterOrder(stopLimitRequest);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 1)));
        assertThat(security.findStopLimitOrderById(1).getStopLimit()).isEqualTo(100);
        assertThat(security.getDeactivatedBuyOrders().size()).isEqualTo(1);
        assertThat(security.getDeactivatedSellOrders().size()).isEqualTo(0);

    }
    @Test
    void check_priority_buy()
    {
        broker1.increaseCreditBy(1000 * 50);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,100,50,1,shareholder.getShareholderId(),0,0,100);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , BUY,100,50,1,shareholder.getShareholderId(),0,0,200);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        orderHandler.handleEnterOrder(stopLimitRequest2);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 1)));
        assertThat(security.findStopLimitOrderById(1).getStopLimit()).isEqualTo(100);
        assertThat(security.getDeactivatedBuyOrders().get(0).getOrderId()).isEqualTo(1);
        assertThat(security.getDeactivatedSellOrders().size()).isEqualTo(0);

    }
    @Test
    void check_priority_sell()
    {
        broker1.increaseCreditBy(1000 * 50);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , SELL,100,50,1,shareholder.getShareholderId(),0,0,100);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , SELL,100,50,1,shareholder.getShareholderId(),0,0,200);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        orderHandler.handleEnterOrder(stopLimitRequest2);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 1)));
        assertThat(security.findStopLimitOrderById(1).getStopLimit()).isEqualTo(100);
        assertThat(security.getDeactivatedSellOrders().get(0).getOrderId()).isEqualTo(2);
        assertThat(security.getDeactivatedBuyOrders().size()).isEqualTo(0);

    }
    @Test
    void stop_limit_will_active_after_some_trade()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,100,50,1,shareholder.getShareholderId(),0,0,100);
        orderHandler.handleEnterOrder(stopLimitRequest);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , SELL,100,100,1,shareholder.getShareholderId(),0,0,0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC" , 3 , LocalDateTime.now() , BUY,50,120,1,shareholder.getShareholderId(),0,0,0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        verify(eventPublisher).publish(new OrderActivatedEvent(3, 1));

    }
    @Test
    void stop_limit_will_active_after_update()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,100,50,1,shareholder.getShareholderId(),0,0,200);
        orderHandler.handleEnterOrder(stopLimitRequest);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , SELL,100,100,1,shareholder.getShareholderId(),0,0,0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC" , 3 , LocalDateTime.now() , BUY,50,120,1,shareholder.getShareholderId(),0,0,0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        assertThat(security.getDeactivatedBuyOrders().get(0)).isNotNull();
        EnterOrderRq updateStopOrderRq = EnterOrderRq.createUpdateOrderRq(4, "ABC", 1, LocalDateTime.now(), BUY, 100, 50,1, 0, 0 , 100);
        System.out.println(updateStopOrderRq.getPeakSize());
        orderHandler.handleEnterOrder(updateStopOrderRq);
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 1));
    }
    


}
