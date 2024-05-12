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
        int openingPrice = this.findOpeningPrice(security);
        LinkedList<Trade> trades = this.match(security, openingPrice);
        for (Trade trade : trades) {
            trade.getSell().getShareholder().decPosition(security, trade.getQuantity());
            trade.getBuy().getShareholder().incPosition(security, trade.getQuantity());
        }
        if (!trades.isEmpty())
            security.setLastTradePrice(openingPrice);
        return trades;
    }

    private LinkedList<Trade> match(Security security, int openingPrice) {
        LinkedList<Trade> trades = new LinkedList<>();
        ArrayList<Order> openedSell = security.getOrderBook().getOpenOrders(openingPrice, Side.SELL);
        ArrayList<Order> openedBuy = security.getOrderBook().getOpenOrders(openingPrice, Side.BUY);
        while (!openedBuy.isEmpty()) {
            Order buyOrder = openedBuy.remove(0);
            if (!openedSell.isEmpty()) {
                trades.addAll(matchBuyOrder(buyOrder, openedSell, openingPrice));
                if (buyOrder.getQuantity() == 0)
                    if (buyOrder instanceof IcebergOrder icebergBuyOrder) {
                        icebergBuyOrder.replenish();
                        if (icebergBuyOrder.getQuantity() != 0)
                            openedBuy.add(icebergBuyOrder);
                        else
                            security.getOrderBook().removeByOrderId(Side.BUY, buyOrder.getOrderId());
                    } else
                        security.getOrderBook().removeByOrderId(Side.BUY, buyOrder.getOrderId());
                else
                    break;
            } else
                break;
        }
        return trades;
    }

    private LinkedList<Trade> matchBuyOrder(Order buyOrder, ArrayList<Order> sells, int price) {
        buyOrder.getBroker().increaseCreditBy(buyOrder.getPrice() * buyOrder.getQuantity());
        LinkedList<Trade> trades = new LinkedList<>();
        while (buyOrder.getQuantity() != 0) {
            if (sells.isEmpty()) {
                if (buyOrder.getQuantity() != 0)
                    buyOrder.getBroker().decreaseCreditBy(buyOrder.getValue());
                break;
            }
            if (sells.get(0).getQuantity() > buyOrder.getQuantity()) {
                trades.add(new Trade(buyOrder.getSecurity(), price, buyOrder.getQuantity(), buyOrder, sells.get(0)));
                buyOrder.getBroker().decreaseCreditBy(price * buyOrder.getQuantity());
                sells.get(0).getBroker().increaseCreditBy(price * buyOrder.getQuantity());
                sells.get(0).decreaseQuantity(buyOrder.getQuantity());
                buyOrder.decreaseQuantity(buyOrder.getQuantity());
                buyOrder.getSecurity().getOrderBook().removeByOrderId(Side.BUY, buyOrder.getOrderId());
            } else {
                trades.add(new Trade(buyOrder.getSecurity(), price, sells.get(0).getQuantity(), buyOrder, sells.get(0)));
                buyOrder.getBroker().decreaseCreditBy(price * sells.get(0).getQuantity());
                sells.get(0).getBroker().increaseCreditBy(price * sells.get(0).getQuantity());
                buyOrder.decreaseQuantity(sells.get(0).getQuantity());
                sells.get(0).decreaseQuantity(sells.get(0).getQuantity());
                if (sells.get(0) instanceof IcebergOrder sell) {
                    sell.replenish();
                    if (sell.getQuantity() == 0)
                        buyOrder.getSecurity().getOrderBook().removeByOrderId(Side.SELL, sells.get(0).getOrderId());
                    else
                        sells.add(sell);
                } else
                    buyOrder.getSecurity().getOrderBook().removeByOrderId(Side.SELL, sells.get(0).getOrderId());
                sells.remove(0);
            }
        }
        return trades;
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
        int openedBuyQuantity = security.getOrderBook().getOpenOrders(price, Side.BUY).stream()
                .mapToInt(Order::getAllQuantity).sum();
        int openedSellQuantity = security.getOrderBook().getOpenOrders(price, Side.SELL).stream()
                .mapToInt(Order::getAllQuantity).sum();
        return Math.min(openedSellQuantity, openedBuyQuantity);
    }

    public int findOpeningPrice(Security security) {
        List<Integer> prices = Stream
                .concat(security.getOrderBook().getBuyQueue().stream(), security.getOrderBook().getSellQueue().stream())
                .map(Order::getPrice).toList();
        int maxTrade = -1;
        int maxPrice = -1;
        for (int price : prices) {
            int openedBuyQuantity = security.getOrderBook().getOpenOrders(price, Side.BUY).stream()
                    .mapToInt(Order::getAllQuantity).sum();
            int openedSellQuantity = security.getOrderBook().getOpenOrders(price, Side.SELL).stream()
                    .mapToInt(Order::getAllQuantity).sum();
            if (min(openedBuyQuantity, openedSellQuantity) > maxTrade) {
                maxTrade = min(openedBuyQuantity, openedSellQuantity);
                maxPrice = price;
            }
            else if (min(openedBuyQuantity, openedSellQuantity) == maxTrade) {
                if (Math.abs(maxPrice - security.getLastTradePrice()) > Math
                        .abs(price - security.getLastTradePrice()))
                    maxPrice = price;
                else if (Math.abs(maxPrice - security.getLastTradePrice()) == Math
                        .abs(price - security.getLastTradePrice()))
                    maxPrice = Math.min(maxPrice, price);
            }
        }
        return maxPrice;
    }

}
