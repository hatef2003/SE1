package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
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
            orderCancellationQueue.deactivatedBuyOrders.remove(stopLimitOrder);
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

        boolean losesPriority = order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder)
                        && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));

        if (updateOrderRq.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);
        if (!losesPriority) {
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
    }

    private MatchResult matchNewOrder(Order order, Matcher matcher) {
        Broker broker = order.getBroker();
        if (order instanceof StopLimitOrder) {
            if (order.getSide() == Side.BUY) {
                if (!broker.hasEnoughCredit((long) order.getQuantity() * order.getPrice()))
                    return MatchResult.notEnoughCredit();
                else
                    broker.decreaseCreditBy((long) order.getQuantity() * order.getPrice());
            }
            if (((StopLimitOrder) order).isActive(lastTradePrice))
                ((StopLimitOrder) order).restoreBrokerCredit();
            else {
                if (order.getSide() == Side.BUY) {
                    orderCancellationQueue.addToDeactivatedBuy((StopLimitOrder) order);
                } else {
                    orderCancellationQueue.addToDeactivatedSell((StopLimitOrder) order);

                }
                return MatchResult.executed(order, List.of());
            }
        }
        MatchResult matchResult = matcher.execute(order);
        if (matchResult.trades().size() != 0)
            this.lastTradePrice = matchResult.trades().getLast().getPrice();
        return matchResult;
    }

    private boolean requestHasEnoughPositions(EnterOrderRq updateOrderRq, Shareholder shareholder, int position) {
        return updateOrderRq.getSide() == Side.BUY || shareholder.hasEnoughPositionsOn(this, position);
    }

    public void setLastTradePrice(int price) {
        lastTradePrice = price;
    }
}
