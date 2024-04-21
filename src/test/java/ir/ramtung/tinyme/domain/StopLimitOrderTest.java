package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
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
import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
class StopLimitOrderTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    @Autowired
    Matcher matcher;
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
    private Broker broker1;
    private Broker broker2;
    private Broker broker3;

    @BeforeEach
    void setupOrderBook() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker1 = Broker.builder().brokerId(1).build();
        broker2 = Broker.builder().brokerId(2).build();
        broker3 = Broker.builder().brokerId(2).build();
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);
        brokerRepository.addBroker(broker3);

        broker1.increaseCreditBy(100_000);
        broker = Broker.builder().brokerId(0).credit(1_000_000L).build();
        shareholder = Shareholder.builder().shareholderId(0).build();
        shareholder.incPosition(security, 100_000);
        List<Order> orders = Arrays.asList(
                new Order(1, security, BUY, 304, 200, broker, shareholder),
                new Order(2, security, BUY, 43, 300, broker, shareholder),
                new Order(3, security, BUY, 445, 200, broker, shareholder),
                new Order(4, security, Side.SELL, 350, 400, broker, shareholder),
                new Order(5, security, Side.SELL, 285, 400, broker, shareholder),
                new Order(6, security, Side.SELL, 800, 400, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
    }
    @Test
    void update_stop_limit_order_while_inactive() {
        security = Security.builder().isin("TEST").build();
        EnterOrderRq createRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(), 1, LocalDateTime.now(),
                BUY, 20, 10, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 10000);
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(2, security.getIsin(), 1, LocalDateTime.now(),
                BUY, 20, 10, 0, 0, 0, 100);

        assertThatNoException().isThrownBy(() -> security.newOrder(createRq, broker, shareholder, matcher));
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateReq, matcher));
        assertThat(security.getDeactivatedOrders().get(0).getStopLimit()).isEqualTo(100);
    }
    @Test
    void trying_to_update_stop_limit_order_while_active() {
        EnterOrderRq createRq = EnterOrderRq.createNewOrderRq(1, security.getIsin(), 1, LocalDateTime.now(),
                BUY, 20, 100, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 100);
        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(2, security.getIsin(), 1, LocalDateTime.now(),
                BUY, 20, 100, 0, 0, 0, 200);

        assertThatNoException().isThrownBy(() -> security.newOrder(createRq, broker, shareholder, matcher));
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.updateOrder(updateReq, matcher));
        assertThat(security.getDeactivatedOrders().get(0).getStopLimit()).isEqualTo(100);
    }

}