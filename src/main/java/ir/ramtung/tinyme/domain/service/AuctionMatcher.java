package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;

import java.util.ArrayList;

import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.min;
import org.springframework.stereotype.Service;
import java.util.stream.Stream;
import java.lang.Math;

@Service
public class AuctionMatcher extends Matcher {
    public LinkedList<Trade> open(Security security) {
        int openingPrice = findOpeningPrice(security);
        LinkedList<Trade> trades = match(security, openingPrice);
        for (Trade trade : trades)
            trade.changeShareholderPositions();
        if (!trades.isEmpty())
            security.setLastTradePrice(openingPrice);
        return trades;
    }

    private void handleBuyOrderZeroQuantity(Order buyOrder, ArrayList<Order> openedBuy) {
        if (buyOrder instanceof IcebergOrder icebergBuyOrder) {
            icebergBuyOrder.replenish();
            if (icebergBuyOrder.getQuantity() != 0) {
                openedBuy.add(icebergBuyOrder);
                return;
            }
        }
        buyOrder.removeFromSecurity();
    }

    private LinkedList<Trade> match(Security security, int openingPrice) {
        LinkedList<Trade> trades = new LinkedList<>();
        ArrayList<Order> openedSell = security.getOrderBook().getOpenOrders(openingPrice, Side.SELL);
        ArrayList<Order> openedBuy = security.getOrderBook().getOpenOrders(openingPrice, Side.BUY);
        while (!openedBuy.isEmpty() && !openedSell.isEmpty()) {
            Order buyOrder = openedBuy.remove(0);
            trades.addAll(matchBuyOrder(buyOrder, openedSell, openingPrice));
            if (buyOrder.getQuantity() == 0)
                handleBuyOrderZeroQuantity(buyOrder, openedBuy);
        }
        return trades;
    }

    private LinkedList<Trade> matchBuyOrder(Order buyOrder, ArrayList<Order> sells, int price) {
        buyOrder.getBroker().increaseCreditBy(getValue(buyOrder.getPrice(), buyOrder.getQuantity()));
        LinkedList<Trade> trades = new LinkedList<>();
        while (buyOrder.getQuantity() != 0) {
            if (sells.isEmpty()) {
                if (buyOrder.getQuantity() != 0)
                    buyOrder.getBroker().decreaseCreditBy(buyOrder.getValue());
                break;
            }
            Order firstSellOrder = sells.get(0);
            if (firstSellOrder.getQuantity() > buyOrder.getQuantity()) {
                trades.add(new Trade(buyOrder.getSecurity(), price, buyOrder.getQuantity(), buyOrder, firstSellOrder));
                buyOrder.getBroker().decreaseCreditBy(getValue(price, buyOrder.getQuantity()));
                firstSellOrder.getBroker().increaseCreditBy(getValue(price, buyOrder.getQuantity()));
                firstSellOrder.decreaseQuantity(buyOrder.getQuantity());
                buyOrder.decreaseQuantity(buyOrder.getQuantity());
                continue;
            }
            trades.add(
                    new Trade(buyOrder.getSecurity(), price, firstSellOrder.getQuantity(), buyOrder, firstSellOrder));
            buyOrder.getBroker().decreaseCreditBy(getValue(price, firstSellOrder.getQuantity()));
            firstSellOrder.getBroker().increaseCreditBy(getValue(price, firstSellOrder.getQuantity()));
            buyOrder.decreaseQuantity(firstSellOrder.getQuantity());
            firstSellOrder.decreaseQuantity(firstSellOrder.getQuantity());
            if (firstSellOrder instanceof IcebergOrder sell) {
                sell.replenish();
                if (sell.getQuantity() == 0)
                    buyOrder.removeFromSecurity();
                else
                    sells.add(sell);
            } else
                buyOrder.removeFromSecurity();
            sells.remove(0);
        }
        return trades;
    }

    private static long getValue(int price, int quantity) {
        return (long) price * quantity;
    }

    @Override
    public MatchResult execute(Order order) {
        if (!order.getBroker().hasEnoughCredit(order.getValue()))
            return MatchResult.notEnoughCredit();
        if (order.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
        order.getSecurity().getOrderBook().enqueue(order);
        return MatchResult.executed(order, List.of());
    }

    public int getTradableQuantity(int price, Security security) {
        int openedBuyQuantity = getOpenOrdersSum(security, price, Side.BUY);
        int openedSellQuantity = getOpenOrdersSum(security, price, Side.SELL);
        return Math.min(openedSellQuantity, openedBuyQuantity);
    }

    public int findOpeningPrice(Security security) {
        List<Integer> prices = getPrices(security);
        prices.add(security.getLastTradePrice());

        int maxTrade = 0;
        int maxPrice = -1;
        for (int price : prices) {
            int openedBuyQuantity = getOpenOrdersSum(security, price, Side.BUY);
            int openedSellQuantity = getOpenOrdersSum(security, price, Side.SELL);
            if (min(openedBuyQuantity, openedSellQuantity) > maxTrade) {
                maxTrade = min(openedBuyQuantity, openedSellQuantity);
                maxPrice = price;
                continue;
            }
            if (min(openedBuyQuantity, openedSellQuantity) == maxTrade) {
                if (isCloserToLastTradePrice(security, maxPrice, price)) {
                    maxPrice = price;
                    continue;
                }
                if (isEquallyCloseToLastTradePrice(security, maxPrice, price))
                    maxPrice = Math.min(maxPrice, price);
            }
        }
        return maxPrice;
    }

    private static ArrayList<Integer> getPrices(Security security) {
        Stream<Order> buyQueueStream = security.getOrderBook().getBuyQueue().stream();
        Stream<Order> sellQueueStream = security.getOrderBook().getSellQueue().stream();
        return new ArrayList<>(Stream.concat(buyQueueStream, sellQueueStream)
                .map(Order::getPrice).toList());
    }

    private static boolean isCloserToLastTradePrice(Security security, int maxPrice, int price) {
        return Math.abs(maxPrice - security.getLastTradePrice()) > Math.abs(price - security.getLastTradePrice());
    }

    private static boolean isEquallyCloseToLastTradePrice(Security security, int maxPrice, int price) {
        return Math.abs(maxPrice - security.getLastTradePrice()) == Math
                .abs(price - security.getLastTradePrice());
    }

    private static int getOpenOrdersSum(Security security, int price, Side side) {
        return security.getOrderBook().getOpenOrders(price, side).stream()
                .mapToInt(Order::getAllQuantity).sum();
    }

}
