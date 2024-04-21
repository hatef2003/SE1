package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
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
import static ir.ramtung.tinyme.messaging.Message.STOP_LIMIT_ORDER_HAS_MINIMUM_EXECUTION_QUANTITY;
import static ir.ramtung.tinyme.messaging.Message.STOP_LIMIT_ORDER_IS_ICEBERG;
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
    @Test
    void buy_stop_limit_active_at_entry_time_and_make_trades()
    {

        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , SELL,100,100,1,shareholder.getShareholderId(),0,0,0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC" , 3 , LocalDateTime.now() , BUY,100,120,1,shareholder.getShareholderId(),0,0,0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        EnterOrderRq matchingSellOrder = EnterOrderRq.createNewOrderRq(4, "ABC", 5, LocalDateTime.now(), SELL, 50, 50, 1, shareholder.getShareholderId(),0);
        orderHandler.handleEnterOrder(matchingSellOrder);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,100,50,1,shareholder.getShareholderId(),0,0,100);
        orderHandler.handleEnterOrder(stopLimitRequest);
        Order matching = new Order (5,security,Side.SELL,50,50,broker1,shareholder);
        Order comingOrder = new Order (1,security,Side.BUY,100,50,broker1,shareholder);
        Trade trade = new Trade(security, 50, 50, matching,comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 1,List.of(new TradeDTO(trade))));
    }

    @Test
    void sell_stop_limit_active_at_entry_time_and_make_trades()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , SELL,100,50,1,shareholder.getShareholderId(),0,0,0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC" , 3 , LocalDateTime.now() , BUY,100,50,1,shareholder.getShareholderId(),0,0,0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        EnterOrderRq matchingBuyOrder = EnterOrderRq.createNewOrderRq(4, "ABC", 5, LocalDateTime.now(), BUY, 50, 50, 1, shareholder.getShareholderId(),0);
        orderHandler.handleEnterOrder(matchingBuyOrder);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , SELL,100,50,1,shareholder.getShareholderId(),0,0,70);
        orderHandler.handleEnterOrder(stopLimitRequest);
        Order matching = new Order (5,security,Side.BUY,50,50,broker1,shareholder);
        Order comingOrder = new Order (1,security, SELL,100,50,broker1,shareholder);
        Trade trade = new Trade(security, 50, 50, matching,comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 1,List.of(new TradeDTO(trade))));
    }
    @Test
    void two_buy_order_activate_together()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , BUY,100,200,1,shareholder.getShareholderId(),0,0,200);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,100,200,1,shareholder.getShareholderId(),0,0,100);

        orderHandler.handleEnterOrder(stopLimitRequest2);
        orderHandler.handleEnterOrder(stopLimitRequest1);

        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(3, "ABC" , 3 , LocalDateTime.now() , SELL,300,200,1,shareholder.getShareholderId(),0,0,0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(4, "ABC" , 4 , LocalDateTime.now() , BUY,100,200,1,shareholder.getShareholderId(),0,0,0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 1));
        Order matching = new Order (3,security, SELL,200,200,broker1,shareholder);
        Order comingOrder = new Order (1,security, BUY,100,200,broker1,shareholder);
        Trade trade = new Trade(security, 200, 100, matching,comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(4, 1,List.of(new TradeDTO(trade))));
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 2));
        Order matching2 = new Order (3,security, SELL,100,200,broker1,shareholder);
        Order comingOrder2 = new Order (2,security, BUY,100,200,broker1,shareholder);
        Trade trade2 = new Trade(security, 200, 100, matching2,comingOrder2);
        verify(eventPublisher).publish(new OrderExecutedEvent(4, 2,List.of(new TradeDTO(trade2))));
    }
    @Test
    void two_sell_order_activate_together()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , SELL,50,200,1,shareholder.getShareholderId(),0,0,200);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , SELL,50,200,1,shareholder.getShareholderId(),0,0,300);

        orderHandler.handleEnterOrder(stopLimitRequest2);
        orderHandler.handleEnterOrder(stopLimitRequest1);

        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(3, "ABC" , 3 , LocalDateTime.now() , SELL,100,200,1,shareholder.getShareholderId(),0,0,0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(4, "ABC" , 4 , LocalDateTime.now() , BUY,150,200,1,shareholder.getShareholderId(),0,0,0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 1));
        Order matching = new Order (4,security, BUY,50,200,broker1,shareholder);
        Order comingOrder = new Order (1,security, SELL,100,200,broker1,shareholder);
        Trade trade = new Trade(security, 200, 50, matching,comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(4, 1,List.of(new TradeDTO(trade))));
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 2));

    }
    @Test
    void buy_activate_on_update_and_match()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,50,200,1,shareholder.getShareholderId(),0,0,300);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(3, "ABC" , 3 , LocalDateTime.now() , SELL,150,200,1,shareholder.getShareholderId(),0,0,0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(4, "ABC" , 4 , LocalDateTime.now() , BUY,100,200,1,shareholder.getShareholderId(),0,0,0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        assertThat(security.getLastTradePrice()).isEqualTo(200);
        EnterOrderRq update = EnterOrderRq.createUpdateOrderRq(5, "ABC",1,LocalDateTime.now(),BUY,40,200,1,shareholder.getShareholderId(),0,100 );
        orderHandler.handleEnterOrder(update);
        verify(eventPublisher).publish(new OrderActivatedEvent(5, 1));
        Order matching = new Order (3,security, SELL,50,200,broker1,shareholder);
        Order comingOrder = new Order (1,security,BUY,40,200,broker1,shareholder);
        Trade trade = new Trade(security, 200, 40, matching,comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(5, 1,List.of(new TradeDTO(trade))));
    }
    @Test
    void error_if_peak_size_is_not_zero()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,50,200,1,shareholder.getShareholderId(),1,0,300);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        verify(eventPublisher).publish((new OrderRejectedEvent(1,1,List.of(STOP_LIMIT_ORDER_IS_ICEBERG))));
        
    }
    @Test
    void error_if_min_execution_is_not_zero()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,50,200,1,shareholder.getShareholderId(),0,1,300);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        verify(eventPublisher).publish((new OrderRejectedEvent(1,1,List.of(STOP_LIMIT_ORDER_HAS_MINIMUM_EXECUTION_QUANTITY))));


    }
    @Test
    void delete_deactivated_order()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,50,200,1,shareholder.getShareholderId(),0,0,300);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        //    public DeleteOrderRq(long requestId, String securityIsin, Side side, long orderId) {
        DeleteOrderRq delete = new DeleteOrderRq(1 , "ABC",Side.BUY , 1);
        orderHandler.handleDeleteOrder(delete);
        verify(eventPublisher).publish(new OrderDeletedEvent(1,1));
        assertThat(security.getDeactivatedOrders().size()).isEqualTo(0);
    }
    @Test
    void an_activated_order_make_another_one_active()
    {
        broker1.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , BUY,100,300,1,shareholder.getShareholderId(),0,0,200);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC" , 1 , LocalDateTime.now() , BUY,100,250,1,shareholder.getShareholderId(),0,0,100);

        orderHandler.handleEnterOrder(stopLimitRequest2);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        EnterOrderRq sellOrder2 = EnterOrderRq.createNewOrderRq(5, "ABC" , 5 , LocalDateTime.now() , SELL,100,250,1,shareholder.getShareholderId(),0,0,0);
        orderHandler.handleEnterOrder(sellOrder2);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(3, "ABC" , 3 , LocalDateTime.now() , SELL,100,100,1,shareholder.getShareholderId(),0,0,0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(4, "ABC" , 4 , LocalDateTime.now() , BUY,100,100,1,shareholder.getShareholderId(),0,0,0);
        orderHandler.handleEnterOrder(buyOrder);
        orderHandler.handleEnterOrder(sellOrder);
        verify(eventPublisher).publish(new OrderActivatedEvent(3,1));
        verify(eventPublisher).publish(new OrderActivatedEvent(3,2));


    }

    @Test
    void broker_has_not_enough_credit()
    {
        broker1.increaseCreditBy(100*300);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC" , 2 , LocalDateTime.now() , BUY,100,300,1,shareholder.getShareholderId(),0,0,200);
        orderHandler.handleEnterOrder(stopLimitRequest2);
        verify(eventPublisher).publish(new OrderAcceptedEvent(2,2));
        EnterOrderRq update = EnterOrderRq.createUpdateOrderRq(1,"ABC", 2, LocalDateTime.now(), BUY, 100, 350,1, shareholder.getShareholderId(), 0,200);
        orderHandler.handleEnterOrder(update);
        verify(eventPublisher).publish(new OrderRejectedEvent(1,2,List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));

    }







}
