package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.min;
import org.springframework.stereotype.Service;

import java.util.stream.Stream;

import java.util.stream.Collectors;

@Service
public class AuctionMatcher extends Matcher {
    private LinkedList<Trade> matchButOrder(Order buy, ArrayList<Order> sells , int price) {
        LinkedList<Trade>trades = new LinkedList<>();
        while (buy.getQuantity() != 0) {
            if(sells.isEmpty())
            {
                break;
            }
            if (sells.get(0).getQuantity() > buy.getQuantity())
            {
                trades.add(new Trade(buy.getSecurity(), price, buy.getQuantity(), buy,sells.get(0)));
                sells.get(0).decreaseQuantity(buy.getQuantity());
                buy.decreaseQuantity(buy.getQuantity());
            }
            else
            {
                trades.add(new Trade(buy.getSecurity(), price, sells.get(0).getQuantity(), buy,sells.get(0)));
                buy.decreaseQuantity(sells.get(0).getQuantity());
                sells.get(0).decreaseQuantity(sells.get(0).getQuantity());
                sells.remove(0);
            }
            
        }
        return trades;
    }

    public MatchResult match(Security security, int openingPrice) {
        ArrayList<Order> openedSell = security.getOrderBook().getOpenedOrders(openingPrice, Side.SELL);
        ArrayList<Order> openedBuy = security.getOrderBook().getOpenedOrders(openingPrice, Side.BUY);
        for (Order buyOrder : openedBuy) {
            if (openedSell.isEmpty()) {
                break;
            }

        }
        return MatchResult.notEnoughTrades();
    }

    public int findOpeningPrice(Security security) {
        List<Integer> prices = Stream
                .concat(security.getOrderBook().getBuyQueue().stream(), security.getOrderBook().getSellQueue().stream())
                .map(order -> order.getPrice()).collect(Collectors.toList());
        int maxTrade = 0;
        int maxPrice = -1;
        for (int price : prices) {
            int openedBuyQuantity = security.getOrderBook().getOpenedOrders(price, Side.BUY).stream()
                    .mapToInt(order -> order.getQuantity()).sum();
            int openedSellQuantity = security.getOrderBook().getOpenedOrders(price, Side.BUY).stream()
                    .mapToInt(order -> order.getQuantity()).sum();
            if (min(openedBuyQuantity, openedSellQuantity) > maxTrade) {
                maxTrade = min(openedBuyQuantity, openedSellQuantity);
                maxPrice = price;
            }
        }

        return maxPrice;
    }

    @Override
    public MatchResult execute(Order order) {
        order.getSecurity().getOrderBook().enqueue(order);
        int openingPrice = this.findOpeningPrice(order.getSecurity());
        return this.match(order.getSecurity(), openingPrice);
    }
}
