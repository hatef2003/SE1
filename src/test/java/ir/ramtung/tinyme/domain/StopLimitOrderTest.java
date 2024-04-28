package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static ir.ramtung.tinyme.messaging.Message.STOP_LIMIT_ORDER_HAS_MINIMUM_EXECUTION_QUANTITY;
import static ir.ramtung.tinyme.messaging.Message.STOP_LIMIT_ORDER_IS_ICEBERG;
import static org.mockito.Mockito.verify;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    private Broker broker;

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000_000);
        shareholderRepository.addShareholder(shareholder);

        broker = Broker.builder().brokerId(1).build();
        brokerRepository.addBroker(broker);
    }

    @Test
    void new_buy_stop_limit_order_created_correctly() {
        broker.increaseCreditBy(1000 * 50);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 100, 50, 1,
                shareholder.getShareholderId(), 0, 0, 100);
        orderHandler.handleEnterOrder(stopLimitRequest);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 1)));
        assertThat(security.getOrderCancellationQueue().findStopLimitOrderById(1).getStopLimit()).isEqualTo(100);
        assertThat(security.getOrderCancellationQueue().getDeactivatedBuyOrders().size()).isEqualTo(1);
        assertThat(security.getOrderCancellationQueue().getDeactivatedSellOrders().size()).isEqualTo(0);
    }

    @Test
    void buy_priority_is_correct() {
        broker.increaseCreditBy(1000 * 50);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 100, 50,
                1, shareholder.getShareholderId(), 0, 0, 100);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), BUY, 100, 50,
                1, shareholder.getShareholderId(), 0, 0, 200);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        orderHandler.handleEnterOrder(stopLimitRequest2);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 1)));
        assertThat(security.getOrderCancellationQueue().findStopLimitOrderById(1).getStopLimit()).isEqualTo(100);
        assertThat(security.getOrderCancellationQueue().getDeactivatedBuyOrders().get(0).getOrderId()).isEqualTo(1);
        assertThat(security.getOrderCancellationQueue().getDeactivatedSellOrders().size()).isEqualTo(0);
    }

    @Test
    void sell_priority_is_correct() {
        broker.increaseCreditBy(1000 * 50);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), SELL, 100, 50,
                1, shareholder.getShareholderId(), 0, 0, 100);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 100, 50,
                1, shareholder.getShareholderId(), 0, 0, 200);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        orderHandler.handleEnterOrder(stopLimitRequest2);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 1)));
        assertThat(security.getOrderCancellationQueue().findStopLimitOrderById(1).getStopLimit()).isEqualTo(100);
        assertThat(security.getOrderCancellationQueue().getDeactivatedSellOrders().get(0).getOrderId()).isEqualTo(2);
        assertThat(security.getOrderCancellationQueue().getDeactivatedBuyOrders().size()).isEqualTo(0);
    }

    @Test
    void stop_limit_order_activates_after_some_trade() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 100, 50, 1,
                shareholder.getShareholderId(), 0, 0, 100);
        orderHandler.handleEnterOrder(stopLimitRequest);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 100, 100, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), BUY, 50, 120, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        verify(eventPublisher).publish(new OrderActivatedEvent(3, 1));
    }

    @Test
    void stop_limit_order_activates_after_update() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 100, 50, 1,
                shareholder.getShareholderId(), 0, 0, 200);
        orderHandler.handleEnterOrder(stopLimitRequest);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 100, 100, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), BUY, 50, 120, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        assertThat(security.getOrderCancellationQueue().getDeactivatedBuyOrders().get(0)).isNotNull();
        EnterOrderRq updateStopOrderRq = EnterOrderRq.createUpdateOrderRq(4, "ABC", 1, LocalDateTime.now(), BUY, 100,
                50, 1, 0, 0, 100);
        System.out.println(updateStopOrderRq.getPeakSize());
        orderHandler.handleEnterOrder(updateStopOrderRq);
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 1));
    }

    @Test
    void buy_stop_limit_activates_on_entry() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 100, 100, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), BUY, 100, 120, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        EnterOrderRq matchingSellOrder = EnterOrderRq.createNewOrderRq(4, "ABC", 5, LocalDateTime.now(), SELL, 50, 50,
                1, shareholder.getShareholderId(), 0);
        orderHandler.handleEnterOrder(matchingSellOrder);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 100, 50, 1,
                shareholder.getShareholderId(), 0, 0, 100);
        orderHandler.handleEnterOrder(stopLimitRequest);
        Order matching = new Order(5, security, Side.SELL, 50, 50, broker, shareholder);
        Order comingOrder = new Order(1, security, Side.BUY, 100, 50, broker, shareholder);
        Trade trade = new Trade(security, 50, 50, matching, comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade))));
    }

    @Test
    void sell_stop_limit_activates_on_entry() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 100, 50, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), BUY, 100, 50, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        EnterOrderRq matchingBuyOrder = EnterOrderRq.createNewOrderRq(4, "ABC", 5, LocalDateTime.now(), BUY, 50, 50, 1,
                shareholder.getShareholderId(), 0);
        orderHandler.handleEnterOrder(matchingBuyOrder);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), SELL, 100, 50,
                1, shareholder.getShareholderId(), 0, 0, 70);
        orderHandler.handleEnterOrder(stopLimitRequest);
        Order matching = new Order(5, security, Side.BUY, 50, 50, broker, shareholder);
        Order comingOrder = new Order(1, security, SELL, 100, 50, broker, shareholder);
        Trade trade = new Trade(security, 50, 50, matching, comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade))));
    }

    @Test
    void two_buy_orders_activate_together() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), BUY, 100, 200,
                1, shareholder.getShareholderId(), 0, 0, 200);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 100, 200,
                1, shareholder.getShareholderId(), 0, 0, 100);

        orderHandler.handleEnterOrder(stopLimitRequest2);
        orderHandler.handleEnterOrder(stopLimitRequest1);

        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), SELL, 300, 200, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(4, "ABC", 4, LocalDateTime.now(), BUY, 100, 200, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 1));
        Order matching = new Order(3, security, SELL, 200, 200, broker, shareholder);
        Order comingOrder = new Order(1, security, BUY, 100, 200, broker, shareholder);
        Trade trade = new Trade(security, 200, 100, matching, comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(4, 1, List.of(new TradeDTO(trade))));
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 2));
        Order matching2 = new Order(3, security, SELL, 100, 200, broker, shareholder);
        Order comingOrder2 = new Order(2, security, BUY, 100, 200, broker, shareholder);
        Trade trade2 = new Trade(security, 200, 100, matching2, comingOrder2);
        verify(eventPublisher).publish(new OrderExecutedEvent(4, 2, List.of(new TradeDTO(trade2))));
    }

    @Test
    void two_sell_orders_activate_together() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 50, 200,
                1, shareholder.getShareholderId(), 0, 0, 200);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), SELL, 50, 200,
                1, shareholder.getShareholderId(), 0, 0, 300);

        orderHandler.handleEnterOrder(stopLimitRequest2);
        orderHandler.handleEnterOrder(stopLimitRequest1);

        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), SELL, 100, 200, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(4, "ABC", 4, LocalDateTime.now(), BUY, 150, 200, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 1));
        Order matching = new Order(4, security, BUY, 50, 200, broker, shareholder);
        Order comingOrder = new Order(1, security, SELL, 100, 200, broker, shareholder);
        Trade trade = new Trade(security, 200, 50, matching, comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(4, 1, List.of(new TradeDTO(trade))));
        verify(eventPublisher).publish(new OrderActivatedEvent(4, 2));
    }

    @Test
    void buy_activates_on_update() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 50, 200,
                1, shareholder.getShareholderId(), 0, 0, 300);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), SELL, 150, 200, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(4, "ABC", 4, LocalDateTime.now(), BUY, 100, 200, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        assertThat(security.getLastTradePrice()).isEqualTo(200);
        EnterOrderRq update = EnterOrderRq.createUpdateOrderRq(5, "ABC", 1, LocalDateTime.now(), BUY, 40, 200, 1,
                shareholder.getShareholderId(), 0, 100);
        orderHandler.handleEnterOrder(update);
        verify(eventPublisher).publish(new OrderActivatedEvent(5, 1));
        Order matching = new Order(3, security, SELL, 50, 200, broker, shareholder);
        Order comingOrder = new Order(1, security, BUY, 40, 200, broker, shareholder);
        Trade trade = new Trade(security, 200, 40, matching, comingOrder);
        verify(eventPublisher).publish(new OrderExecutedEvent(5, 1, List.of(new TradeDTO(trade))));
    }

    @Test
    void stop_limit_order_with_peak_size_rejected() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 50, 200,
                1, shareholder.getShareholderId(), 1, 0, 300);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        verify(eventPublisher).publish((new OrderRejectedEvent(1, 1, List.of(STOP_LIMIT_ORDER_IS_ICEBERG))));
    }

    @Test
    void stop_limit_order_with_min_execution_rejected() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 50, 200,
                1, shareholder.getShareholderId(), 0, 1, 300);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        verify(eventPublisher)
                .publish((new OrderRejectedEvent(1, 1, List.of(STOP_LIMIT_ORDER_HAS_MINIMUM_EXECUTION_QUANTITY))));
    }

    @Test
    void activate_order_gets_removed_from_deactivated_list() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 50, 200,
                1, shareholder.getShareholderId(), 0, 0, 300);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        DeleteOrderRq delete = new DeleteOrderRq(1, "ABC", Side.BUY, 1);
        orderHandler.handleDeleteOrder(delete);
        verify(eventPublisher).publish(new OrderDeletedEvent(1, 1));
        assertThat(security.getOrderCancellationQueue().getDeactivatedBuyOrders().size()
                + security.getOrderCancellationQueue().getDeactivatedSellOrders().size()).isEqualTo(0);
    }

    @Test
    void activated_order_activates_another() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), BUY, 100, 300,
                1, shareholder.getShareholderId(), 0, 0, 200);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 100, 250,
                1, shareholder.getShareholderId(), 0, 0, 100);

        orderHandler.handleEnterOrder(stopLimitRequest2);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        EnterOrderRq sellOrder2 = EnterOrderRq.createNewOrderRq(5, "ABC", 5, LocalDateTime.now(), SELL, 100, 250, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder2);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), SELL, 100, 100, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(4, "ABC", 4, LocalDateTime.now(), BUY, 100, 100, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(buyOrder);
        orderHandler.handleEnterOrder(sellOrder);
        verify(eventPublisher).publish(new OrderActivatedEvent(3, 1));
        verify(eventPublisher).publish(new OrderActivatedEvent(3, 2));
    }

    @Test
    void order_with_insufficient_broker_credit_rejected() {
        broker.increaseCreditBy(100 * 300);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), BUY, 100, 300,
                1, shareholder.getShareholderId(), 0, 0, 200);
        orderHandler.handleEnterOrder(stopLimitRequest2);
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 2));
        EnterOrderRq update = EnterOrderRq.createUpdateOrderRq(1, "ABC", 2, LocalDateTime.now(), BUY, 100, 350, 1,
                shareholder.getShareholderId(), 0, 200);
        orderHandler.handleEnterOrder(update);
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 2, List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
    }

    @Test
    void updating_stop_limit_order_changes_broker_credit_correctly() {
        broker.increaseCreditBy(1000 * 50);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC", 1,
                LocalDateTime.now(), BUY, 100, 50, 1, shareholder.getShareholderId(), 0, 0, 100);
        EnterOrderRq update = EnterOrderRq.createUpdateOrderRq(2, security.getIsin(), 1, LocalDateTime.now(),
                BUY, 100, 45, 1, 0, 0, 100);
        orderHandler.handleEnterOrder(stopLimitRequest);
        long creditBeforeUpdate = broker.getCredit();
        assertThat(creditBeforeUpdate).isEqualTo(1000 * 50 - 100 * 50);
        orderHandler.handleEnterOrder(update);
        assertThat(broker.getCredit()).isEqualTo(creditBeforeUpdate + 500);
    }

    @Test
    void activated_sell_order_activates_another() {
        broker.increaseCreditBy(100_000_000);
        EnterOrderRq stopLimitRequest2 = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 100, 100,
                1, shareholder.getShareholderId(), 0, 0, 200);
        EnterOrderRq stopLimitRequest1 = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), SELL, 100, 250,
                1, shareholder.getShareholderId(), 0, 0, 100);

        orderHandler.handleEnterOrder(stopLimitRequest2);
        orderHandler.handleEnterOrder(stopLimitRequest1);
        EnterOrderRq buyOrder2 = EnterOrderRq.createNewOrderRq(5, "ABC", 5, LocalDateTime.now(), BUY, 100, 100, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(buyOrder2);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), BUY, 100, 200, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(4, "ABC", 4, LocalDateTime.now(), SELL, 100, 200, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        verify(eventPublisher).publish(new OrderActivatedEvent(3, 1));
        verify(eventPublisher).publish(new OrderActivatedEvent(3, 2));
    }

    @Test 
    void broker_credit_before_activate_is_correct()
    {
        broker.increaseCreditBy(200 * 50);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), BUY, 50, 100,
                1, shareholder.getShareholderId(), 0, 0, 200);
        orderHandler.handleEnterOrder(stopLimitRequest);
        assertThat(broker.getCredit()).isEqualTo(100*50);
    }

    @Test 
    void broker_credit_after_activate_is_correct()
    { 
        Broker broker2;
        broker2 = Broker.builder().brokerId(2).build();
        brokerRepository.addBroker(broker2);
        broker.increaseCreditBy(100_000);
        EnterOrderRq sellOrder = EnterOrderRq.createNewOrderRq(2, "ABC", 2, LocalDateTime.now(), SELL, 100, 50, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        EnterOrderRq buyOrder = EnterOrderRq.createNewOrderRq(3, "ABC", 3, LocalDateTime.now(), BUY, 100, 50, 1,
                shareholder.getShareholderId(), 0, 0, 0);
        orderHandler.handleEnterOrder(sellOrder);
        orderHandler.handleEnterOrder(buyOrder);
        assertThat(broker.getCredit()).isEqualTo(100_000);
        EnterOrderRq stopLimitRequest = EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(), BUY, 100, 50,
                1, shareholder.getShareholderId(), 0, 0, 45);
        EnterOrderRq matchingSellOrder = EnterOrderRq.createNewOrderRq(5, "ABC",6,LocalDateTime.now(),Side.SELL,100,45,broker2.getBrokerId(),shareholder.getShareholderId(),0 );
        orderHandler.handleEnterOrder(matchingSellOrder);
        orderHandler.handleEnterOrder(stopLimitRequest);
        assertThat(broker.getCredit()).isEqualTo(100_000 - 100*45);
    }
}
