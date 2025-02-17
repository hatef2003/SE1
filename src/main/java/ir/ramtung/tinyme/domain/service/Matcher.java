package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.exception.NotEnoughCreditException;

import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.ListIterator;

@Service
public class Matcher {
    public MatchResult match(Order newOrder) {
        newOrder.removeFromSecurity();

        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();

        while (newOrder.canMatch() && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;
            Trade trade;
            try {
                trade = makeTrade(matchingOrder, newOrder, trades);
            } catch (NotEnoughCreditException Ex) {
                return MatchResult.notEnoughCredit();
            }
            trade.increaseSellersCredit();
            trades.add(trade);
            matchTwoOrder(matchingOrder, newOrder, orderBook);
        }

       return handleMinimumExceptionQuantity(trades, newOrder);
    }

    private MatchResult handleMinimumExceptionQuantity(LinkedList<Trade> trades, Order newOrder) {
        if (isTradeBiggerThanMEQ(newOrder, trades))
        {
            newOrder.makeMinimumExceptionZero();
            return MatchResult.executed(newOrder, trades);
        }
        if (newOrder.getSide() == Side.SELL)
            rollbackSellTrades(newOrder, trades);
        else
            rollbackBuyTrades(newOrder, trades);
        return MatchResult.notEnoughTrades();
    }

    private static boolean isTradeBiggerThanMEQ(Order newOrder, LinkedList<Trade> trades) {
        int tradeQuantity = trades.stream().mapToInt(Trade::getQuantity).sum();
        return tradeQuantity >= newOrder.getMinimumExecutionQuantity();
    }

    private Trade makeTrade(Order matchingOrder, Order newOrder, LinkedList<Trade> trades)
            throws NotEnoughCreditException {
        int tradeQuantity = Math.min(newOrder.getQuantity(), matchingOrder.getQuantity());
        Trade trade = new Trade(newOrder.getSecurity(), matchingOrder.getPrice(),
                tradeQuantity, newOrder, matchingOrder);
        if (newOrder.getSide() == Side.BUY) {
            if (trade.buyerHasEnoughCredit())
                trade.decreaseBuyersCredit();
            else {
                rollbackBuyTrades(newOrder, trades);
                throw new NotEnoughCreditException();
            }
        }
        return trade;
    }

    public void matchTwoOrder(Order matchingOrder, Order newOrder, OrderBook orderBook) {
        if (newOrder.getQuantity() >= matchingOrder.getQuantity()) {
            newOrder.decreaseQuantity(matchingOrder.getQuantity());
            orderBook.removeFirst(matchingOrder.getSide());
            if (matchingOrder instanceof IcebergOrder icebergOrder) {
                icebergOrder.decreaseQuantity(matchingOrder.getQuantity());
                icebergOrder.replenish();
                if (icebergOrder.getQuantity() > 0)
                    orderBook.enqueue(icebergOrder);
            }
        } else {
            matchingOrder.decreaseQuantity(newOrder.getQuantity());
            newOrder.makeQuantityZero();
        }
    }

    protected void rollbackBuyTrades(Order newOrder, LinkedList<Trade> trades) {
        assert newOrder.getSide() == Side.BUY;
        long totalTradeValue = trades.stream().mapToLong(Trade::getTradedValue).sum();
        newOrder.getBroker().increaseCreditBy(totalTradeValue);
        trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));

        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreSellOrder(it.previous().getSell());
        }
    }

    protected void rollbackSellTrades(Order newOrder, LinkedList<Trade> trades) {
        assert newOrder.getSide() == Side.SELL;
        for (Trade trade : trades) {
            newOrder.getBroker().decreaseCreditBy(getValue(trade.getPrice(), trade.getQuantity()));
            newOrder.getSecurity().getOrderBook().restoreBuyOrder(trade.getBuy());
        }
    }

    public MatchResult execute(Order order) {
        MatchResult result = match(order);
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT ||
                result.outcome() == MatchingOutcome.NOT_ENOUGH_TRADE)
            return result;

        if (result.remainder().getQuantity() > 0) {
            if (order.getSide() == Side.BUY) {
                if (!order.getBroker().hasEnoughCredit(order.getValue())) {
                    rollbackBuyTrades(order, result.trades());
                    return MatchResult.notEnoughCredit();
                }
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }
        if (!result.trades().isEmpty()) {
            for (Trade trade : result.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
        return result;
    }

    protected static long getValue(int price, int quantity) {
        return (long) price * quantity;
    }

}
