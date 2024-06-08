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

    public MatchResult newOrder(Order order, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (!requestHasEnoughPositions(order, shareholder,
                orderBook.totalSellQuantityByShareholder(shareholder) + order.getQuantity()))
            return MatchResult.notEnoughPositions();
        return matchNewOrder(order, matcher);
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = getOrderFromRequest(deleteOrderRq);
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order instanceof StopLimitOrder) {
            if (order.getSide() == Side.BUY)
                ((StopLimitOrder) order).restoreBrokerCredit();
            orderCancellationQueue.removeFromDeactivatedList(order.getOrderId());
        } else {
            if (order.getSide() == Side.BUY)
                order.getBroker().increaseCreditBy(order.getValue());
            orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        }
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = getOrderFromRequest(updateOrderRq);
        if (order instanceof StopLimitOrder)
            return updateValidOrder((StopLimitOrder) order, updateOrderRq);
        else
            return updateValidOrder(order, updateOrderRq, matcher);
    }

    public Order getOrderFromRequest(EnterOrderRq updateOrderRq) {
        Order bookOrder = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        Order cancelledOrder = orderCancellationQueue.findStopLimitOrderById(updateOrderRq.getOrderId());
        return bookOrder != null ? bookOrder : cancelledOrder;
    }

    private Order getOrderFromRequest(DeleteOrderRq updateOrderRq) {
        Order bookOrder = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        Order cancelledOrder = orderCancellationQueue.findStopLimitOrderById(updateOrderRq.getOrderId());
        return bookOrder != null ? bookOrder : cancelledOrder;
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
        int position = orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity()
                + updateOrderRq.getQuantity();
        if (!requestHasEnoughPositions(order, order.getShareholder(), position))
            return MatchResult.notEnoughPositions();
        Order firstOrderBeforeAnyChange = order.snapshot();

        if (updateOrderRq.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);
        if (!losesPriority(firstOrderBeforeAnyChange, updateOrderRq)) {
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

    private boolean losesPriority(Order order, EnterOrderRq updateOrderRq) {
        return order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder)
                        && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));
    }

    private MatchResult matchNewOrder(Order order, Matcher matcher) {
        if (order instanceof StopLimitOrder) {
            Broker broker = order.getBroker();
            return matchStopLimitOrder((StopLimitOrder) order, matcher, broker);
        } else {
            MatchResult matchResult = matcher.execute(order);
            if (matchResult.trades().size() != 0)
                lastTradePrice = matchResult.trades().getLast().getPrice();
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
                lastTradePrice = matchResult.trades().getLast().getPrice();
            return matchResult;
        } else {
            if (stopLimitOrder.getSide() == Side.BUY)
                orderCancellationQueue.addToDeactivatedBuy(stopLimitOrder);
            else
                orderCancellationQueue.addToDeactivatedSell(stopLimitOrder);
            return MatchResult.executed(stopLimitOrder, List.of());
        }
    }

    private boolean requestHasEnoughPositions(Order order, Shareholder shareholder, int position) {
        return order.getSide() == Side.BUY || shareholder.hasEnoughPositionsOn(this, position);
    }

    public void setLastTradePrice(int price) {
        lastTradePrice = price;
    }

    public void changeMatchingStateRq(MatchingState targetState) {
        state = targetState;
    }

}
