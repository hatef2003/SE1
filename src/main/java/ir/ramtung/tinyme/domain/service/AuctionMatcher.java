package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.min;
import org.springframework.stereotype.Service;

import java.util.stream.Stream;

import java.util.stream.Collectors;
import java.lang.Math;

@Service
public class AuctionMatcher extends Matcher {
    private LinkedList<Trade> matchBuyOrder(Order buy, ArrayList<Order> sells, int price) {
        buy.getBroker().increaseCreditBy(buy.getPrice() * buy.getQuantity());
        LinkedList<Trade> trades = new LinkedList<>();
        while (buy.getQuantity() != 0) {
            if (sells.isEmpty()) {
                if (buy.getQuantity() != 0) {
                    buy.getBroker().decreaseCreditBy(buy.getValue() * buy.getQuantity());
                }
                break;
            }
            if (sells.get(0).getQuantity() > buy.getQuantity()) {
                trades.add(new Trade(buy.getSecurity(), price, buy.getQuantity(), buy, sells.get(0)));
                buy.getBroker().decreaseCreditBy(price * buy.getQuantity());
                sells.get(0).decreaseQuantity(buy.getQuantity());
                buy.decreaseQuantity(buy.getQuantity());
                buy.getSecurity().getOrderBook().removeByOrderId(Side.BUY, buy.getOrderId());

            } else {
                trades.add(new Trade(buy.getSecurity(), price, sells.get(0).getQuantity(), buy, sells.get(0)));
                buy.getBroker().decreaseCreditBy(price * sells.get(0).getQuantity());
                buy.decreaseQuantity(sells.get(0).getQuantity());
                sells.get(0).decreaseQuantity(sells.get(0).getQuantity());
                if (sells.get(0) instanceof IcebergOrder) {
                    IcebergOrder sell = (IcebergOrder) sells.get(0);
                    sell.replenish();
                    if (sell.getQuantity() == 0)
                        buy.getSecurity().getOrderBook().removeByOrderId(Side.SELL, sells.get(0).getOrderId());
                    else
                        sells.add(sell);
                } else {
                    buy.getSecurity().getOrderBook().removeByOrderId(Side.SELL, sells.get(0).getOrderId());
                }
                sells.remove(0);
            }

        }
        return trades;
    }

    public MatchResult match(Security security, int openingPrice, Order newOrder) {
        LinkedList<Trade> allTrades = new LinkedList<>();
        ArrayList<Order> openedSell = security.getOrderBook().getOpenedOrders(openingPrice, Side.SELL);
        ArrayList<Order> openedBuy = security.getOrderBook().getOpenedOrders(openingPrice, Side.BUY);
        while (!openedBuy.isEmpty()) {
            Order buyOrder = openedBuy.get(0);
            openedBuy.remove(0);
            if (openedSell.isEmpty()) {
                break;
            } else {
                allTrades.addAll(matchBuyOrder(buyOrder, openedSell, openingPrice));
                if (buyOrder.getQuantity() == 0)
                    if (buyOrder instanceof IcebergOrder) {
                        IcebergOrder icebergBuyOrder = (IcebergOrder) buyOrder;
                        icebergBuyOrder.replenish();
                        if (icebergBuyOrder.getQuantity() != 0) {
                            openedBuy.add(icebergBuyOrder);
                        } else {
                            security.getOrderBook().removeByOrderId(Side.BUY, buyOrder.getOrderId());
                        }

                    } else {
                        security.getOrderBook().removeByOrderId(Side.BUY, buyOrder.getOrderId());
                    }

            }

        }
        return MatchResult.executed(newOrder, allTrades);
    }

    public int findOpeningPrice(Security security) {
        List<Integer> prices = Stream
                .concat(security.getOrderBook().getBuyQueue().stream(), security.getOrderBook().getSellQueue().stream())
                .map(order -> order.getPrice()).collect(Collectors.toList());
        int maxTrade = -1;
        int maxPrice = -1;
        for (int price : prices) {
            int openedBuyQuantity = security.getOrderBook().getOpenedOrders(price, Side.BUY).stream()
                    .mapToInt(order -> order.getAllQuantity()).sum();
            int openedSellQuantity = security.getOrderBook().getOpenedOrders(price, Side.SELL).stream()
                    .mapToInt(order -> order.getAllQuantity()).sum();
            if (min(openedBuyQuantity, openedSellQuantity) > maxTrade) {
                maxTrade = min(openedBuyQuantity, openedSellQuantity);
                maxPrice = price;
            }
            if (min(openedBuyQuantity, openedSellQuantity) == maxTrade) {
                if (Math.abs(maxPrice - security.getLastTradePrice()) > Math
                        .abs(price - security.getLastTradePrice())) {
                    maxPrice = price;
                }
                if (Math.abs(maxPrice - security.getLastTradePrice()) == Math
                        .abs(price - security.getLastTradePrice())) {
                    maxPrice = Math.min(maxPrice, price);
                }
            }
        }
        return maxPrice;
    }

    @Override
    public MatchResult execute(Order order) {
        order.getSecurity().getOrderBook().enqueue(order);
        int openingPrice = this.findOpeningPrice(order.getSecurity());
        return this.match(order.getSecurity(), openingPrice, order);
    }
}
