package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
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

import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

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

        assertThatNoException().isThrownBy(() -> orderHandler.handleChangeMatchingStateRq(new ChangeMatchingStateRq(security.getIsin(), MatchingState.AUCTION, 1)));
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
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        int openingPrice = auctionMatcher.findOpeningPrice(security);
        assertThat(openingPrice).isEqualTo(15490);
        assertThat(auctionMatcher.getTradableQuantity(15490 , security)).isEqualTo(285);
    }

    @Test
    void auction_matcher_open_price_multiple_trades_with_same_quantity() {
        security.setLastTradePrice(30000);
        List<Order> orders = Arrays.asList(
                new Order(4, security, Side.BUY, 200, 20000, broker, shareholder),
                new Order(5, security, Side.BUY, 200, 10000, broker, shareholder),
                new Order(7, security, Side.SELL, 200, 10000, broker, shareholder),
                new Order(8, security, Side.SELL, 100, 20000, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        int openingPrice = auctionMatcher.findOpeningPrice(security);
        assertThat(openingPrice).isEqualTo(20000);
        assertThat(auctionMatcher.getTradableQuantity(20000 , security)).isEqualTo(200);
    }

    @Test
    void auction_matcher_open_price_trades_with_same_quantity_and_equally_close_to_last_trade_price() {
        security.setLastTradePrice(15000);
        List<Order> orders = Arrays.asList(
                new Order(4, security, Side.BUY, 200, 20000, broker, shareholder),
                new Order(5, security, Side.BUY, 200, 10000, broker, shareholder),
                new Order(7, security, Side.SELL, 200, 10000, broker, shareholder),
                new Order(8, security, Side.SELL, 100, 20000, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        int openingPrice = auctionMatcher.findOpeningPrice(security);
        assertThat(openingPrice).isEqualTo(10000);
        assertThat(auctionMatcher.getTradableQuantity(10000 , security)).isEqualTo(200);
    }

    @Test
    void auction_matcher_matches_one_trade() {
        shareholder.incPosition(security,100_000_000);
        broker.increaseCreditBy(100_000_000);

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
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        var result = auctionMatcher.open(security);
        Trade supposedTrade = new Trade(security, 15490, 285, orders.get(0), orders.get(6));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).containsExactly(supposedTrade);
    }
}
