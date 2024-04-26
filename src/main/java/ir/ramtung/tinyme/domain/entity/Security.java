package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import lombok.Builder;
import lombok.Getter;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
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
    ArrayList<StopLimitOrder> deactivatedOrders = new ArrayList<>();
    @Builder.Default
    ArrayList<StopLimitOrder> deactivatedBuyOrders = new ArrayList<>();
    @Builder.Default
    ArrayList<StopLimitOrder> deactivatedSellOrders = new ArrayList<>();
    @Builder.Default
    private int lastTradePrice = -1;

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                        orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();
        Order order;
        if (enterOrderRq.getPeakSize() == 0 && enterOrderRq.getStopLimit() == 0)
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity());
        else if (enterOrderRq.getStopLimit() != 0 && enterOrderRq.getPeakSize() == 0
                && enterOrderRq.getMinimumExecutionQuantity() == 0) {
            if (enterOrderRq.getSide() == Side.BUY) {
                if (!broker.hasEnoughCredit((long) enterOrderRq.getQuantity() * enterOrderRq.getPrice()))
                    return MatchResult.notEnoughCredit();
                else
                    broker.decreaseCreditBy((long) enterOrderRq.getQuantity() * enterOrderRq.getPrice());
            }
            StopLimitOrder newOrder = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), OrderStatus.NEW, enterOrderRq.getStopLimit());
            if (newOrder.isActive(lastTradePrice)) {
                newOrder.restoreBrokerCredit();
                order = new Order(newOrder);
            } else {
                deactivatedOrders.add(newOrder);
                if (enterOrderRq.getSide() == Side.BUY)
                    addToDeactivatedBuy(newOrder);
                else
                    addToDeactivatedSell(newOrder);
                return MatchResult.executed(newOrder, new LinkedList<>());
            }
        } else
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(),
                    enterOrderRq.getMinimumExecutionQuantity());

        MatchResult matchResult = matcher.execute(order);
        if (matchResult.trades().size() != 0)
            lastTradePrice = matchResult.trades().getLast().getPrice();

        return matchResult;
    }

    public void setLastTradePrice(int price) {
        lastTradePrice = price;
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        StopLimitOrder stopLimitOrder = findStopLimitOrderById(deleteOrderRq.getOrderId());
        if (order == null && stopLimitOrder == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order != null) {
            if (order.getSide() == Side.BUY)
                order.getBroker().increaseCreditBy(order.getValue());
            orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        } else {
            if (stopLimitOrder.getSide() == Side.BUY)
                stopLimitOrder.restoreBrokerCredit();
            removeFromDeactivatedList(stopLimitOrder.getOrderId());
        }

    }
    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        StopLimitOrder stopLimitOrder = findStopLimitOrderById(updateOrderRq.getOrderId());
        if (order == null && stopLimitOrder == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order != null) {
            if (updateOrderRq.getStopLimit() != 0) {
                throw new InvalidRequestException(Message.ACTIVATED_ORDER_CANT_HAS_STOP_LIMIT);
            }
            if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
                throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
            if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
                throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);

            if (updateOrderRq.getSide() == Side.SELL &&
                    !order.getShareholder().hasEnoughPositionsOn(this,
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
                if (updateOrderRq.getSide() == Side.BUY) {
                    originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
                }
            }
            return matchResult;
        } else {
            if (stopLimitOrder.side == BUY) {
                stopLimitOrder.restoreBrokerCredit();
                deactivatedBuyOrders.remove(stopLimitOrder);
                if (stopLimitOrder.getBroker().hasEnoughCredit((long) updateOrderRq.getPrice() * updateOrderRq.getQuantity()))
                {
                    stopLimitOrder.updateFromRequest(updateOrderRq);
                    addToDeactivatedBuy(stopLimitOrder);
                }
                else
                    return MatchResult.notEnoughCredit();
            } else {
                stopLimitOrder.updateFromRequest(updateOrderRq);
                deactivatedSellOrders.remove(stopLimitOrder);
                addToDeactivatedSell(stopLimitOrder);
            }
            return MatchResult.executed(stopLimitOrder, new LinkedList<>());
        }
    }
    public StopLimitOrder findStopLimitOrderById(long id) {
        for (var order : deactivatedOrders)
            if (order.getOrderId() == id)
                return order;
        return null;
    }
    private void addToDeactivatedBuy(StopLimitOrder newOrder) {
        deactivatedBuyOrders.add(newOrder);
        deactivatedBuyOrders.sort(Comparator.comparingDouble(StopLimitOrder::getStopLimit));
    }
    private void addToDeactivatedSell(StopLimitOrder newOrder) {
        deactivatedSellOrders.add(newOrder);
        deactivatedSellOrders.sort(Comparator.comparingDouble(StopLimitOrder::getStopLimit).reversed());
    }
    public void removeFromDeactivatedList(long id) {
        StopLimitOrder order = findStopLimitOrderById(id);
        if (order != null) {
            deactivatedOrders.remove(order);
            if (order.getSide() == Side.BUY)
                deactivatedBuyOrders.remove(order);
            else
                deactivatedSellOrders.remove(order);
        }
    }
}
