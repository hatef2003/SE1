package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
class MinimumExecutionQuantity {
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
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
    }
    @Test
    void buy_order_not_enough_trade_rollback()
    {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new Order(2, security, BUY, 200, 15850, broker, shareholder,100);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(security.getOrderBook().getSellQueue().get(0).getQuantity()).isEqualTo(65);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADE);
    }
    @Test
    void buy_order_has_enough_trade()
    {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new Order(2 , security , BUY , 200 , 15850 , broker , shareholder , 65);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }
    @Test
    void sell_order_not_enough_trade_rollback()
    {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new Order(2, security, SELL, 500, 15650, broker, shareholder,350);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(304);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADE);
    }
    @Test
    void sell_order_has_enough_trade()
    {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new Order(2, security, SELL, 500, 15650, broker, shareholder, 304);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade + (15700 * 304));
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }
    @Test
    void iceberg_buy_order_not_enough_trade_rollback()
    {
        long brokerCreditBeforeTrade = broker.getCredit();
        IcebergOrder newOrder = new IcebergOrder(2, security, BUY, 200, 15850, broker, shareholder,
                100, 100);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(security.getOrderBook().getSellQueue().get(0).getQuantity()).isEqualTo(65);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADE);
    }
    @Test
    void iceberg_buy_order_has_enough_trade()
    {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new IcebergOrder(2 , security , BUY , 200 , 15850 , broker , shareholder ,
                100, 65);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }
    @Test
    void iceberg_sell_order_not_enough_trade_rollback()
    {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new IcebergOrder(2, security, SELL, 500, 15650, broker, shareholder,
                100, 350);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(304);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADE);
    }
    @Test
    void iceberg_sell_order_has_enough_trade()
    {
        long brokerCreditBeforeTrade = broker.getCredit();
        Order newOrder = new IcebergOrder(2, security, SELL, 500, 15650, broker, shareholder,
                100, 304);
        MatchResult result = matcher.match(newOrder);
        assertThat(broker.getCredit()).isEqualTo(brokerCreditBeforeTrade + (15700 * 304));
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
    }

}