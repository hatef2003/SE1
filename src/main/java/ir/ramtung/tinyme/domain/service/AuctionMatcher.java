package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.ListIterator;

@Service
public class AuctionMatcher extends Matcher{
    public MatchResult match(Security security , int openingPrice)  {
        //TODO Auction matching
     return MatchResult.notEnoughTrades();
    }
    public int findOpeningPrice(Security security)
    {
        //TODO
        return 0 ;
    }


    @Override
    public MatchResult execute(Order order) {
        order.getSecurity().getOrderBook().enqueue(order);
        int openingPrice = this.findOpeningPrice(order.getSecurity());
        return this.match(order);
    }
}
