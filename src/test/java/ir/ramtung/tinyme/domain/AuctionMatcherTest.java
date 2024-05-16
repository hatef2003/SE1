package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class AuctionMatcherTest {
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
    private AuctionMatcher auctionMatcher;

    @BeforeEach
    void setup() {
        auctionMatcher = new AuctionMatcher();
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker = Broker.builder().brokerId(1).build();
        brokerRepository.addBroker(broker);

        assertThatNoException().isThrownBy(() -> orderHandler
                .handleChangeMatchingStateRq(new ChangeMatchingStateRq(1, security.getIsin(), MatchingState.AUCTION)));
    }

    @Test
    void auction_matcher_finds_opening_price() {
        security.setLastTradePrice(15450);
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder),
                new Order(7, security, Side.SELL, 285, 15490, broker, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder));
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        int openingPrice = auctionMatcher.findOpeningPrice(security);
        assertThat(openingPrice).isEqualTo(15490);
        assertThat(auctionMatcher.getTradableQuantity(15490, security)).isEqualTo(285);
    }

    @Test
    void auction_matcher_open_price_multiple_trades_with_same_quantity() {
        security.setLastTradePrice(30000);
        List<Order> orders = Arrays.asList(
                new Order(4, security, Side.BUY, 200, 20000, broker, shareholder),
                new Order(5, security, Side.BUY, 200, 10000, broker, shareholder),
                new Order(7, security, Side.SELL, 200, 10000, broker, shareholder),
                new Order(8, security, Side.SELL, 100, 20000, broker, shareholder));
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        int openingPrice = auctionMatcher.findOpeningPrice(security);
        assertThat(openingPrice).isEqualTo(20000);
        assertThat(auctionMatcher.getTradableQuantity(20000, security)).isEqualTo(200);
    }

    @Test
    void auction_matcher_open_price_trades_with_same_quantity_and_equally_close_to_last_trade_price() {
        security.setLastTradePrice(15000);
        List<Order> orders = Arrays.asList(
                new Order(4, security, Side.BUY, 100, 20000, broker, shareholder),
                new Order(5, security, Side.BUY, 200, 10000, broker, shareholder),
                new Order(7, security, Side.SELL, 200, 10000, broker, shareholder),
                new Order(8, security, Side.SELL, 100, 20000, broker, shareholder));
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        int openingPrice = auctionMatcher.findOpeningPrice(security);
        assertThat(openingPrice).isEqualTo(10000);
        assertThat(auctionMatcher.getTradableQuantity(10000, security)).isEqualTo(200);
    }

    @Test
    void auction_matcher_matches_one_trade() {
        shareholder.incPosition(security, 100_000_000);
        broker.increaseCreditBy(10_000_000);
        System.out.println(broker.getCredit());
        security.setLastTradePrice(15450);
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, broker, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, broker, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, broker, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, broker, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, broker, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder),
                new Order(7, security, Side.SELL, 285, 15490, broker, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder));
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        Trade supposedTrade = new Trade(security, 15490, 285, orders.get(0), orders.get(6));
        var result = auctionMatcher.open(security);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).containsExactly(supposedTrade);
        assertThat(broker.getCredit()).isEqualTo(14_474_500);
    }

    @Test
    void auction_matcher_execute_with_not_enough_credit() {
        shareholder.incPosition(security, 100_000_000);
        broker.increaseCreditBy(1000_000);

        security.setLastTradePrice(15450);
        Order order = new Order(1, security, Side.BUY, 304, 15700, broker, shareholder);
        var result = auctionMatcher.execute(order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_CREDIT);
        assertThat(broker.getCredit()).isEqualTo(1000_000);
    }

    @Test
    void auction_matcher_execute_with_enough_credit() {
        shareholder.incPosition(security, 100_000_000);
        broker.increaseCreditBy(10_000_000);

        security.setLastTradePrice(15450);
        Order order = new Order(1, security, Side.BUY, 304, 15700, broker, shareholder);
        var result = auctionMatcher.execute(order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(broker.getCredit()).isEqualTo(5_227_200);
    }

    @Test
    void order_handler_only_accepts_auction_appropriate_orders() {
        shareholder.incPosition(security, 100_000_000);
        broker.increaseCreditBy(10_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(), 1, LocalDateTime.now(),
                Side.BUY, 304, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, security.getIsin(), 2, LocalDateTime.now(),
                Side.BUY, 43, 15500, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, security.getIsin(), 3, LocalDateTime.now(),
                Side.BUY, 445, 15450, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(7, security.getIsin(), 7, LocalDateTime.now(),
                Side.SELL, 285, 15490, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(11, security.getIsin(), 11, LocalDateTime.now(),
                Side.SELL, 65, 15820, broker.getBrokerId(), shareholder.getShareholderId(), 0, 20));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(12, security.getIsin(), 12, LocalDateTime.now(),
                Side.SELL, 65, 15820, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 19));

        verify(eventPublisher).publish(new OrderRejectedEvent(3, 3, List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
        verify(eventPublisher).publish(
                new OrderRejectedEvent(11, 11, List.of(Message.AUCTION_CANNOT_HANDLE_MINIMUM_EXECUTION_QUANTITY)));
        verify(eventPublisher)
                .publish(new OrderRejectedEvent(12, 12, List.of(Message.AUCTION_CANNOT_HANDLE_STOP_LIMIT_ORDER)));
        verify(eventPublisher).publish(new OrderAcceptedEvent(7, 7));
    }

    @Test
    void order_handler_changes_from_auction_to_auction_with_trades_correctly() {
        shareholder.incPosition(security, 100_000_000);
        broker.increaseCreditBy(10_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(), 1, LocalDateTime.now(),
                Side.BUY, 304, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, security.getIsin(), 2, LocalDateTime.now(),
                Side.BUY, 43, 15500, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(7, security.getIsin(), 7, LocalDateTime.now(),
                Side.SELL, 285, 15490, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        assertThat(broker.getCredit()).isEqualTo(10_000_000 - (304 * 15700 + 43 * 15500));

        assertThatNoException().isThrownBy(() -> orderHandler
                .handleChangeMatchingStateRq(new ChangeMatchingStateRq(1, security.getIsin(), MatchingState.AUCTION)));

        verify(eventPublisher, atLeast(2)).publish(new OpeningPriceEvent(security.getIsin(), -1, 0));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 15490, 285));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 1));
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 2));
        verify(eventPublisher).publish(new OrderAcceptedEvent(7, 7));
        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), 15490, 285, 1, 7));
        verify(eventPublisher, atLeast(2))
                .publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.AUCTION));
        assertThat(broker.getCredit()).isEqualTo(10_000_000 - (285 * 15490 + 19 * 15700 + 43 * 15500) + 285 * 15490);

    }

    @Test
    void order_handler_changes_from_auction_to_continuous_with_trades_correctly() {
        shareholder.incPosition(security, 100_000_000);
        broker.increaseCreditBy(10_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(), 1, LocalDateTime.now(),
                Side.BUY, 304, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, security.getIsin(), 2, LocalDateTime.now(),
                Side.BUY, 43, 15500, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(7, security.getIsin(), 7, LocalDateTime.now(),
                Side.SELL, 285, 15490, broker.getBrokerId(), shareholder.getShareholderId(), 0));

        assertThatNoException().isThrownBy(() -> orderHandler.handleChangeMatchingStateRq(
                new ChangeMatchingStateRq(1, security.getIsin(), MatchingState.CONTINUOUS)));

        verify(eventPublisher, atLeast(2)).publish(new OpeningPriceEvent(security.getIsin(), -1, 0));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 15490, 285));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 1));
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 2));
        verify(eventPublisher).publish(new OrderAcceptedEvent(7, 7));
        verify(eventPublisher).publish(new TradeEvent(security.getIsin(), 15490, 285, 1, 7));
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.AUCTION));
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.CONTINUOUS));
    }

    @Test
    void auction_with_iceberg() {
        shareholder.incPosition(security, 100_000_000);
        broker.increaseCreditBy(20_000_000);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(), 1, LocalDateTime.now(),
                Side.BUY, 304, 15700, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, security.getIsin(), 2, LocalDateTime.now(),
                Side.BUY, 43, 15500, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(7, security.getIsin(), 7, LocalDateTime.now(),
                Side.SELL, 85, 15490, broker.getBrokerId(), shareholder.getShareholderId(), 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(8, security.getIsin(), 8, LocalDateTime.now(),
                Side.SELL, 300, 15580, broker.getBrokerId(), shareholder.getShareholderId(), 100));

        assertThatNoException().isThrownBy(() -> orderHandler.handleChangeMatchingStateRq(
                new ChangeMatchingStateRq(1, security.getIsin(), MatchingState.CONTINUOUS)));

        verify(eventPublisher, atLeast(2)).publish(new OpeningPriceEvent(security.getIsin(), -1, 0));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 15490, 85));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 15580, 304));

        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 1));
        verify(eventPublisher).publish(new OrderAcceptedEvent(2, 2));
        verify(eventPublisher).publish(new OrderAcceptedEvent(7, 7));
        verify(eventPublisher).publish(new OrderAcceptedEvent(8, 8));
        verify(eventPublisher, atLeast(2)).publish(new TradeEvent(security.getIsin(), 15580, 100, 1, 8));
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.AUCTION));
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.CONTINUOUS));
    }

    @Test
    void auction_matcher_does_not_calculate_opening_price_when_no_valid_trade_found() {
        List<Order> newOrders = Arrays.asList(
                new Order(1, security, Side.SELL, 200, 25000, broker, shareholder),
                new Order(1, security, Side.BUY, 20, 5000, broker, shareholder));
        newOrders.forEach(order -> security.getOrderBook().enqueue(order));

        int openingPrice = auctionMatcher.findOpeningPrice(security);
        assertThat(openingPrice).isEqualTo(-1);
    }

    @Test
    void range() {
        security.setLastTradePrice(7000);
        List<Order> newOrders = Arrays.asList(
                new Order(1, security, Side.SELL, 200, 5000, broker, shareholder),
                new Order(2, security, Side.BUY, 20, 10000, broker, shareholder));
        newOrders.forEach(order -> security.getOrderBook().enqueue(order));

        int openingPrice = auctionMatcher.findOpeningPrice(security);
        assertThat(openingPrice).isEqualTo(7000);
    }

    @Test
    void multiple_orders_open_and_match_correctly() {
        List<Order> newOrders = Arrays.asList(
                new Order(1, security, Side.SELL, 100, 50, broker, shareholder),
                new Order(1, security, Side.SELL, 100, 100, broker, shareholder),
                new Order(1, security, Side.SELL, 100, 120, broker, shareholder),
                new Order(1, security, Side.SELL, 100, 160, broker, shareholder),
                new Order(1, security, Side.BUY, 100, 140, broker, shareholder),
                new Order(1, security, Side.BUY, 100, 110, broker, shareholder),
                new Order(1, security, Side.BUY, 100, 100, broker, shareholder),
                new Order(1, security, Side.BUY, 100, 45, broker, shareholder));
        newOrders.forEach(order -> security.getOrderBook().enqueue(order));
        assertThat(auctionMatcher.findOpeningPrice(security)).isEqualTo(100);
        assertThat(auctionMatcher.getTradableQuantity(100, security)).isEqualTo(200);
    }

    @Test
    void stop_limit_order_actives_on_auction_matching() {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(10, security, Side.BUY, 50, 140, broker, shareholder,
                LocalDateTime.now(), OrderStatus.NEW, 90);
        security.getOrderCancellationQueue().addToDeactivatedBuy(stopLimitOrder);
        List<Order> newOrders = Arrays.asList(
                new Order(1, security, Side.SELL, 100, 50, broker, shareholder),
                new Order(2, security, Side.SELL, 100, 100, broker, shareholder),
                new Order(3, security, Side.SELL, 100, 120, broker, shareholder),
                new Order(4, security, Side.SELL, 100, 160, broker, shareholder),
                new Order(5, security, Side.BUY, 100, 140, broker, shareholder),
                new Order(6, security, Side.BUY, 100, 110, broker, shareholder),
                new Order(7, security, Side.BUY, 100, 100, broker, shareholder),
                new Order(8, security, Side.BUY, 100, 45, broker, shareholder));
        newOrders.forEach(order -> security.getOrderBook().enqueue(order));
        assertThat(auctionMatcher.findOpeningPrice(security)).isEqualTo(100);
        assertThat(auctionMatcher.getTradableQuantity(100, security)).isEqualTo(200);
        assertThatNoException().isThrownBy(() -> orderHandler.handleChangeMatchingStateRq(
                new ChangeMatchingStateRq(1, security.getIsin(), MatchingState.AUCTION)));
        verify(eventPublisher).publish(new OrderActivatedEvent(1, 10));
    }

    @Test
    void iceberg_order_unchanged_after_matching() {
        List<Order> newOrders = Arrays.asList(
                new Order(1, security, Side.SELL, 100, 50, broker, shareholder),
                new Order(2, security, Side.SELL, 100, 100, broker, shareholder),
                new Order(3, security, Side.SELL, 100, 120, broker, shareholder),
                new Order(4, security, Side.SELL, 100, 160, broker, shareholder),
                new IcebergOrder(5, security, Side.BUY, 100, 140, broker, shareholder,10),
                new Order(6, security, Side.BUY, 100, 110, broker, shareholder),
                new Order(7, security, Side.BUY, 100, 100, broker, shareholder),
                new Order(8, security, Side.BUY, 100, 45, broker, shareholder));
        newOrders.forEach(order -> security.getOrderBook().enqueue(order));
        assertThat(auctionMatcher.findOpeningPrice(security)).isEqualTo(100);
        assertThat(auctionMatcher.getTradableQuantity(100, security)).isEqualTo(200);
        assertThatNoException().isThrownBy(() -> orderHandler.handleChangeMatchingStateRq(
                new ChangeMatchingStateRq(1, security.getIsin(), MatchingState.AUCTION)));
        assertThat(security.getOrderBook().getBuyQueue().get(0).getOrderId()).isEqualTo(5);
    }
}
