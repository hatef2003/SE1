package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.validation.Validation;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.ChangeMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderHandler {
    private final SecurityRepository securityRepository;
    private final BrokerRepository brokerRepository;
    private final ShareholderRepository shareholderRepository;
    private final EventPublisher eventPublisher;
    private final Matcher matcher;
    private final AuctionMatcher auctionMatcher;
    private final Validation validation;
    private final OrderFactory orderFactory;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository,
            ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
        this.auctionMatcher = new AuctionMatcher();
        orderFactory = new OrderFactory(securityRepository, shareholderRepository, brokerRepository);
        validation = new Validation();
    }

    private void activateStopLimitOrders(Security security, long request_id) {
        Matcher securityMatcher = (security.getState() == MatchingState.AUCTION) ? auctionMatcher : matcher;
        ArrayList<StopLimitOrder> activatedList = security.getOrderCancellationQueue()
                .getActivatedOrder(security.getLastTradePrice());
        for (int i = 0; i < activatedList.size(); i++) {
            StopLimitOrder stopLimitOrder = activatedList.get(i);
            stopLimitOrder.restoreBrokerCredit();
            Order newOrder = new Order(stopLimitOrder);
            MatchResult result = securityMatcher.execute(newOrder);
            eventPublisher.publish(new OrderActivatedEvent(request_id, stopLimitOrder.getOrderId()));
            if (!result.trades().isEmpty()) {
                eventPublisher.publish(new OrderExecutedEvent(request_id, stopLimitOrder.getOrderId(),
                        result.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                int lastTradePrice = result.trades().get(result.trades().size() - 1).getPrice();
                newOrder.getSecurity().setLastTradePrice(lastTradePrice);
                activatedList
                        .addAll(security.getOrderCancellationQueue().getActivatedOrder(security.getLastTradePrice()));
            }
        }
    }

    private void publishMatchError(MatchingOutcome matchingOutcome, EnterOrderRq enterOrderRq) {
        if (matchingOutcome == MatchingOutcome.NOT_ENOUGH_CREDIT)
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
        else if (matchingOutcome == MatchingOutcome.NOT_ENOUGH_POSITIONS)
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
        else if (matchingOutcome == MatchingOutcome.NOT_ENOUGH_TRADE)
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.TRADE_QUANTITY_LESS_THAN_MINIMUM)));
    }

    private void publishExecutedOrderEvents(EnterOrderRq enterOrderRq, MatchResult matchResult, Security security) {
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        else
            eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));

        if (security.getState() == MatchingState.AUCTION)
            publishOpenPriceEvent(security);

        if (!matchResult.trades().isEmpty())
            eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            
            
            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
            Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());
            validation.validate(enterOrderRq,security,broker,shareholder);
            Matcher securityMatcher = (security.getState() == MatchingState.AUCTION) ? auctionMatcher : matcher;

            MatchResult matchResult;
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                matchResult = security.newOrder(orderFactory.createOrder(enterOrderRq), broker, shareholder,
                        securityMatcher);
            else
                matchResult = security.updateOrder(enterOrderRq, securityMatcher);

            if (matchResult.outcome() == MatchingOutcome.EXECUTED) {
                publishExecutedOrderEvents(enterOrderRq, matchResult, security);
                activateStopLimitOrders(security, enterOrderRq.getRequestId());
            } else
                publishMatchError(matchResult.outcome(), enterOrderRq);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(
                    new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            security.deleteOrder(deleteOrderRq);
            eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
            if (security.getState() == MatchingState.AUCTION)
                publishOpenPriceEvent(security);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(
                    new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    public void handleChangeMatchingStateRq(ChangeMatchingStateRq changeMatchingStateRq)
            throws InvalidRequestException {
        Security security = securityRepository.findSecurityByIsin(changeMatchingStateRq.getSecurityIsin());
        if (security == null)
            throw new InvalidRequestException(Message.UNKNOWN_SECURITY_ISIN);

        eventPublisher
                .publish(new SecurityStateChangedEvent(security.getIsin(), changeMatchingStateRq.getTargetState()));
        if (security.getState() == MatchingState.AUCTION) {
            LinkedList<Trade> trades = auctionMatcher.open(security);
            publishTradeEvent(trades);
            security.changeMatchingStateRq(changeMatchingStateRq.getTargetState());
            if (!trades.isEmpty())
                activateStopLimitOrders(security, changeMatchingStateRq.getRequestId());
        } else
            security.changeMatchingStateRq(changeMatchingStateRq.getTargetState());
    }

    private void publishOpenPriceEvent(Security security) {
        int openingPrice = auctionMatcher.findOpeningPrice(security);
        int tradableQuantity = auctionMatcher.getTradableQuantity(openingPrice, security);
        eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), openingPrice, tradableQuantity));
    }

    private void publishTradeEvent(LinkedList<Trade> trades) {
        for (Trade trade : trades)
            eventPublisher.publish(new TradeEvent(trade));
    }

    private void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);

        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security != null) {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        } else
            errors.add(Message.UNKNOWN_SECURITY_ISIN);

        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
        if (enterOrderRq.getPeakSize() != 0 && enterOrderRq.getQuantity() < enterOrderRq.getPeakSize())
            errors.add(Message.PEAK_SIZE_MUST_BE_LESS_THAN_TOTAL_QUANTITY);
        if (enterOrderRq.getStopLimit() != 0 && enterOrderRq.getPeakSize() != 0)
            errors.add(Message.STOP_LIMIT_ORDER_IS_ICEBERG);
        if (enterOrderRq.getStopLimit() != 0 &&
                enterOrderRq.getMinimumExecutionQuantity() > 0)
            errors.add(Message.STOP_LIMIT_ORDER_HAS_MINIMUM_EXECUTION_QUANTITY);
        if (enterOrderRq.getStopLimit() < 0)
            errors.add(Message.INVALID_STOP_LIMIT);

        // Auction Validation
        // if (securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin()) !=
        // null) {
        // MatchingState state =
        // securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin()).getState();
        // if (state == MatchingState.AUCTION &&
        // enterOrderRq.getMinimumExecutionQuantity() != 0)
        // errors.add(Message.AUCTION_CANNOT_HANDLE_MINIMUM_EXECUTION_QUANTITY);
        // if (state == MatchingState.AUCTION && enterOrderRq.getStopLimit() != 0)
        // errors.add(Message.AUCTION_CANNOT_HANDLE_STOP_LIMIT_ORDER);
        // }

        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }
}
