package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
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
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Matcher matcher;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository,
            ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
    }

    private void handleActivatedOrderSortedByTime(Security security, long request_id) {
        ArrayList<StopLimitOrder> activatedList = new ArrayList<StopLimitOrder>();
        for (var stopLimitOrder : security.getDeactivatedOrders()) {
            if (stopLimitOrder.isActive(security.getLastTradePrice())) {

                activatedList.add(stopLimitOrder);
            }
        }
        for (var activated : activatedList) {
            security.removeFromDeactivatedList(activated.getOrderId());
        }
        for (StopLimitOrder stopLimitOrder : activatedList) {
            stopLimitOrder.restoreBrokerCredit();
            Order newOrder = new Order(stopLimitOrder);
            MatchResult result = matcher.execute(newOrder);
            eventPublisher.publish(new OrderActivatedEvent(request_id, stopLimitOrder.getOrderId()));
            if (!result.trades().isEmpty()) {
                eventPublisher.publish(new OrderExecutedEvent(request_id, stopLimitOrder.getOrderId(),
                        result.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                int lastTradePrice = result.trades().get(result.trades().size() - 1).getPrice();
                newOrder.getSecurity().setLastTradePrice(lastTradePrice);
                for (var deactivatedOrder : security.getDeactivatedOrders()) {
                    if (deactivatedOrder.isActive(lastTradePrice)) {
                        activatedList.add(deactivatedOrder);
                        security.removeFromDeactivatedList(deactivatedOrder.getOrderId());
                    }
                }
            }

        }
    }

    private void handleActivatedOrderSortedByStopPrice(Security security, long request_id) {
        ArrayList<StopLimitOrder> activatedList = new ArrayList<>();
        for (var order : security.getDeactivatedBuyOrders()) {
            if (order.isActive(security.getLastTradePrice())) {
                activatedList.add(order);
            }
        }
        for (var order : security.getDeactivatedSellOrders()) {
            if (order.isActive(security.getLastTradePrice())) {
                activatedList.add(order);
            }
        }
        for (var order: activatedList)
        {
            security.removeFromDeactivatedList(order.getOrderId());
        }
        for (StopLimitOrder stopLimitOrder : activatedList) {
            stopLimitOrder.restoreBrokerCredit();
            Order newOrder = new Order(stopLimitOrder);
            MatchResult result = matcher.execute(newOrder);
            eventPublisher.publish(new OrderActivatedEvent(request_id, stopLimitOrder.getOrderId()));
            if (!result.trades().isEmpty()) {
                eventPublisher.publish(new OrderExecutedEvent(request_id, stopLimitOrder.getOrderId(),
                        result.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                int lastTradePrice = result.trades().get(result.trades().size() - 1).getPrice();
                newOrder.getSecurity().setLastTradePrice(lastTradePrice);
                for (var deactivatedOrder : security.getDeactivatedBuyOrders()) {
                    if (deactivatedOrder.isActive(lastTradePrice)) {
                        activatedList.add(deactivatedOrder);
                        security.removeFromDeactivatedList(deactivatedOrder.getOrderId());
                    }
                }
                for (var deactivatedOrder : security.getDeactivatedSellOrders()) {
                    if (deactivatedOrder.isActive(lastTradePrice)) {
                        activatedList.add(deactivatedOrder);
                        security.removeFromDeactivatedList(deactivatedOrder.getOrderId());
                    }
                }
            }

        }
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            validateEnterOrderRq(enterOrderRq);

            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
            Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());

            MatchResult matchResult;
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                matchResult = security.newOrder(enterOrderRq, broker, shareholder, matcher);
            else
                matchResult = security.updateOrder(enterOrderRq, matcher);

            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                        List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
                return;
            }
            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                        List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
                return;
            }
            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_TRADE) {
                eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                        List.of(Message.TRADE_QUANTITY_LESS_THAN_MINIMUM)));
                return;
            }
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER) {
                eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
            } else
                eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
            if (!matchResult.trades().isEmpty()|| enterOrderRq.getRequestType() == OrderEntryType.UPDATE_ORDER) {
                eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                        matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                handleActivatedOrderSortedByTime(security, enterOrderRq.getRequestId());
            }
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
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(
                    new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    private void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        // TODO check Update and Creation of new type off Order (StopLimitOrder)
        List<String> errors = new LinkedList<>();
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        }
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
        if (enterOrderRq.getStopLimit() != 0 && enterOrderRq.getPeakSize() != 0)
            errors.add(Message.STOP_LIMIT_ORDER_IS_ICEBERG);
        if (enterOrderRq.getStopLimit() != 0 && enterOrderRq.getMinimumExecutionQuantity() > 0)
            errors.add(Message.STOP_LIMIT_ORDER_HAS_MINIMUM_EXECUTION_QUANTITY);
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
