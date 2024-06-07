package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import lombok.Builder;
import lombok.Getter;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;

import java.util.List;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    private int lastTradePrice = -1;
    @Builder.Default
    private OrderCancellationQueue orderCancellationQueue = new OrderCancellationQueue();
    @Builder.Default
    private MatchingState state = MatchingState.CONTINUOUS;

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (!requestHasEnoughPositions(enterOrderRq, shareholder,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();
        try {
            validateEnterOrderRq(enterOrderRq);
        } catch (InvalidRequestException ex) {
            return MatchResult.invalidRequest();
        }
        Order order;
        if (enterOrderRq.getPeakSize() != 0)
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(),
                    enterOrderRq.getMinimumExecutionQuantity());
        else if (enterOrderRq.getStopLimit() != 0) {
            order = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), OrderStatus.NEW, enterOrderRq.getStopLimit());
        } else
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity());

        return matchNewOrder(order, matcher);
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        StopLimitOrder stopLimitOrder = orderCancellationQueue.findStopLimitOrderById(deleteOrderRq.getOrderId());
        if (order == null && stopLimitOrder == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order != null) {
            if (order.getSide() == Side.BUY)
                order.getBroker().increaseCreditBy(order.getValue());
            orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        } else {
            if (stopLimitOrder.getSide() == Side.BUY)
                stopLimitOrder.restoreBrokerCredit();
            orderCancellationQueue.removeFromDeactivatedList(stopLimitOrder.getOrderId());
        }
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null)
            order = orderCancellationQueue.findStopLimitOrderById(updateOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);

        validateEnterOrderRq(updateOrderRq);
        if (!(order instanceof StopLimitOrder)) {
            if (updateOrderRq.getStopLimit() != 0)
                throw new InvalidRequestException(Message.ACTIVE_ORDER_CANT_HAVE_STOP_LIMIT);
            if (order instanceof IcebergOrder && updateOrderRq.getPeakSize() == 0)
                throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
            if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
                throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);

            return updateValidOrder(order, updateOrderRq, matcher);
        } else
            return updateValidOrder((StopLimitOrder) order, updateOrderRq);
    }

    private MatchResult updateValidOrder(StopLimitOrder stopLimitOrder, EnterOrderRq updateOrderRq) {
        if (stopLimitOrder.side == BUY) {
            stopLimitOrder.restoreBrokerCredit();
            orderCancellationQueue.removeFromDeactivatedList(stopLimitOrder.orderId);
            if (stopLimitOrder.getBroker()
                    .hasEnoughCredit((long) updateOrderRq.getPrice() * updateOrderRq.getQuantity())) {
                stopLimitOrder.getBroker()
                        .decreaseCreditBy((long) updateOrderRq.getPrice() * updateOrderRq.getQuantity());
                stopLimitOrder.updateFromRequest(updateOrderRq);
                orderCancellationQueue.addToDeactivatedBuy(stopLimitOrder);
            } else
                return MatchResult.notEnoughCredit();
        } else {
            stopLimitOrder.updateFromRequest(updateOrderRq);
            orderCancellationQueue.removeFromDeactivatedList(stopLimitOrder.getOrderId());
            orderCancellationQueue.addToDeactivatedSell(stopLimitOrder);
        }
        return MatchResult.executed(stopLimitOrder, List.of());
    }

    private MatchResult updateValidOrder(Order order, EnterOrderRq updateOrderRq, Matcher matcher) {
        if (!requestHasEnoughPositions(updateOrderRq, order.getShareholder(),
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity()
                        + updateOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();
        Order firstOrderBeforeAnyChange = order.snapshot();
   

        if (updateOrderRq.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);
        if (!loosesPriority(firstOrderBeforeAnyChange, updateOrderRq)) {
            if (updateOrderRq.getSide() == Side.BUY)
                order.getBroker().decreaseCreditBy(order.getValue());
            return MatchResult.executed(null, List.of());
        } else
            order.markAsNew();
        MatchResult matchResult = matcher.execute(order);
        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            orderBook.enqueue(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY)
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
        }
        return matchResult;
    }

    private boolean loosesPriority(Order order, EnterOrderRq updateOrderRq)
    {
        return  order.isQuantityIncreased(updateOrderRq.getQuantity())
        || updateOrderRq.getPrice() != order.getPrice()
        || ((order instanceof IcebergOrder icebergOrder)
                && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));
    }

    private void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        if (enterOrderRq.getPrice() <= 0)
            throw new InvalidRequestException(Message.INVALID_PRICE);
        if (enterOrderRq.getStopLimit() < 0)
            throw new InvalidRequestException(Message.INVALID_STOP_LIMIT);
        if (enterOrderRq.getStopLimit() != 0 && enterOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.STOP_LIMIT_ORDER_IS_ICEBERG);
        if (enterOrderRq.getStopLimit() != 0 && enterOrderRq.getMinimumExecutionQuantity() != 0)
            throw new InvalidRequestException(Message.STOP_LIMIT_ORDER_HAS_MINIMUM_EXECUTION_QUANTITY);
        if (enterOrderRq.getPeakSize() != 0 && enterOrderRq.getQuantity() < enterOrderRq.getPeakSize())
            throw new InvalidRequestException(Message.PEAK_SIZE_MUST_BE_LESS_THAN_TOTAL_QUANTITY);
        if (state == MatchingState.AUCTION && enterOrderRq.getStopLimit() != 0)
            throw new InvalidRequestException(Message.INVALID_STOP_LIMIT_DURING_AUCTION_MATCHING);
        if (state == MatchingState.AUCTION && enterOrderRq.getMinimumExecutionQuantity() != 0)
            throw new InvalidRequestException(Message.INVALID_MINIMUM_EXECUTION_QUANTITY_DURING_AUCTION_MATCHING);
    }

    private MatchResult matchNewOrder(Order order, Matcher matcher) {
        Broker broker = order.getBroker();
        if (order instanceof StopLimitOrder) {
            return matchStopLimitOrder((StopLimitOrder) order, matcher, broker);
        } else {
            MatchResult matchResult = matcher.execute(order);
            if (matchResult.trades().size() != 0)
                this.lastTradePrice = matchResult.trades().getLast().getPrice();
            return matchResult;
        }
    }

    private MatchResult matchStopLimitOrder(StopLimitOrder stopLimitOrder, Matcher matcher, Broker broker) {
        if (stopLimitOrder.getSide() == Side.BUY) {
            if (!broker.hasEnoughCredit((long) stopLimitOrder.getQuantity() * stopLimitOrder.getPrice()))
                return MatchResult.notEnoughCredit();
            else
                broker.decreaseCreditBy((long) stopLimitOrder.getQuantity() * stopLimitOrder.getPrice());
        }
        if (stopLimitOrder.isActive(lastTradePrice)) {
            stopLimitOrder.restoreBrokerCredit();
            MatchResult matchResult = matcher.execute(stopLimitOrder);
            if (matchResult.trades().size() != 0)
                this.lastTradePrice = matchResult.trades().getLast().getPrice();
            return matchResult;
        } else {
            if (stopLimitOrder.getSide() == Side.BUY)
                orderCancellationQueue.addToDeactivatedBuy(stopLimitOrder);
            else
                orderCancellationQueue.addToDeactivatedSell(stopLimitOrder);
            return MatchResult.executed(stopLimitOrder, List.of());
        }
    }

    private boolean requestHasEnoughPositions(EnterOrderRq updateOrderRq, Shareholder shareholder, int position) {
        return updateOrderRq.getSide() == Side.BUY || shareholder.hasEnoughPositionsOn(this, position);
    }

    public void setLastTradePrice(int price) {
        lastTradePrice = price;
    }

    public void changeMatchingStateRq(MatchingState targetState) {
        this.state = targetState;
    }

    public boolean hasOrder(Side orderSide, long orderId)
    {
        return orderBook.findByOrderId(orderSide, orderId) != null;
    }
}
