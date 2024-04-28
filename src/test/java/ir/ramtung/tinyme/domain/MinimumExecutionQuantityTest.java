package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
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

@SpringBootTest
@Import(MockedJMSTestConfig.class)
class MinimumExecutionQuantityTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    @Autowired
    Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        List<Order> orders;
        security = Security.builder().build();
        broker = Broker.builder().brokerId(0).credit(1_000_000_000L).build();
        shareholder = Shareholder.builder().shareholderId(0).build();
        shareholder.incPosition(security, 100_000);
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder));
        orders.forEach(order -> security.getOrderBook().enqueue(order));
    }

    @Test
    void buy_order_not_enough_trade_rollback() {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new Order(2, security, BUY, 200, 15850, broker, shareholder, 100);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(security.getOrderBook().getSellQueue().get(0).getQuantity()).isEqualTo(65);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADE);
    }

    @Test
    void buy_order_has_enough_trade() {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new Order(2, security, BUY, 200, 15850, broker, shareholder, 65);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }

    @Test
    void sell_order_not_enough_trade_rollback() {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new Order(2, security, SELL, 500, 15650, broker, shareholder, 350);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(304);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADE);
    }

    @Test
    void sell_order_has_enough_trade() {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new Order(2, security, SELL, 500, 15650, broker, shareholder, 304);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade + (15700 * 304));
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }

    @Test
    void iceberg_buy_order_not_enough_trade_rollback() {
        long brokerCreditBeforeTrade = broker.getCredit();
        IcebergOrder newOrder = new IcebergOrder(2, security, BUY, 200, 15850, broker, shareholder,
                100, 100);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(security.getOrderBook().getSellQueue().get(0).getQuantity()).isEqualTo(65);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADE);
    }

    @Test
    void iceberg_buy_order_has_enough_trade() {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new IcebergOrder(2, security, BUY, 200, 15850, broker, shareholder,
                100, 65);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }

    @Test
    void iceberg_sell_order_not_enough_trade_rollback() {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new IcebergOrder(2, security, SELL, 500, 15650, broker, shareholder,
                100, 350);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(304);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADE);
    }

    @Test
    void iceberg_sell_order_has_enough_trade() {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new IcebergOrder(2, security, SELL, 500, 15650, broker, shareholder,
                100, 304);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade + (15700 * 304));
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }

    @Test
    void new_order_by_request_not_enough_trade() {
        long broker_credit_before_trade = broker.getCredit();
        EnterOrderRq newOrderRequest = EnterOrderRq.createNewOrderRq(0, security.getIsin(), 5, LocalDateTime.now(), BUY,
                200, 15850, 0, shareholder.getShareholderId(), 0, 100);
        MatchResult result = security.newOrder(newOrderRequest, broker, shareholder, matcher);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADE);
        assertThat(broker.getCredit()).isEqualTo(broker_credit_before_trade);
        assertThat(security.getOrderBook().getSellQueue().get(0).getQuantity()).isEqualTo(65);
    }

    @Test
    void new_order_by_request_enough_trade() {
        long broker_credit_before_trade = broker.getCredit();
        EnterOrderRq newOrderRequest = EnterOrderRq.createNewOrderRq(0, security.getIsin(), 5, LocalDateTime.now(), BUY,
                55, 15850, 0, shareholder.getShareholderId(), 0, 45);
        MatchResult result = security.newOrder(newOrderRequest, broker, shareholder, matcher);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(broker.getCredit()).isEqualTo(broker_credit_before_trade);
        assertThat(security.getOrderBook().getSellQueue().get(0).getQuantity()).isEqualTo(10);
    }

    @Test
    void update_order_check() {
        EnterOrderRq newOrderRequest = EnterOrderRq.createNewOrderRq(0, security.getIsin(), 2, LocalDateTime.now(), BUY,
                200, 15850, 0, shareholder.getShareholderId(), 0, 65);
        Order newOrder2 = new Order(9, security, SELL, 55, 18000, broker, shareholder);
        security.newOrder(newOrderRequest, broker, shareholder, matcher);

        var a = security.getOrderBook().findByOrderId(BUY, 2);
        System.out.println(a);
        matcher.match(newOrder2);
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(0, security.getIsin(), 2, LocalDateTime.now(),
                BUY, 135, 18000, 0, shareholder.getShareholderId(), 0);
        MatchResult result2;
        try {
            result2 = security.updateOrder(updateOrderRq, matcher);
            assertThat(result2.outcome()).isEqualTo(MatchingOutcome.EXECUTED);

        } catch (Exception e) {
            System.out.println("Something wrong");
        }
    }
}