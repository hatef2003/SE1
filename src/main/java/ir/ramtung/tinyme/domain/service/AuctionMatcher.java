package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;


import static java.lang.Math.min;
import org.springframework.stereotype.Service;

import java.util.stream.Stream;

import java.util.stream.Collectors;

@Service
public class AuctionMatcher extends Matcher {
    public MatchResult match(Security security, int openingPrice) {
        // TODO Auction matching
        return MatchResult.notEnoughTrades();
    }

    public int findOpeningPrice(Security security) {
        List<Integer> prices = Stream
                .concat(security.getOrderBook().getBuyQueue().stream(), security.getOrderBook().getSellQueue().stream())
                .map(order -> order.getPrice()).collect(Collectors.toList());
        int maxTrade = 0 ;
        int maxPrice = -1 ; 
        for (int price : prices)
        {
            int openedBuyQuantity = security.getOrderBook().getOpenedBuyOrders(price, Side.BUY).stream().mapToInt(order -> order.getQuantity()).sum();
            int openedSellQuantity =  security.getOrderBook().getOpenedBuyOrders(price, Side.BUY).stream().mapToInt(order -> order.getQuantity()).sum();
            if (min(openedBuyQuantity,openedSellQuantity)>maxTrade)
            {
                maxTrade = min(openedBuyQuantity,openedSellQuantity);
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
