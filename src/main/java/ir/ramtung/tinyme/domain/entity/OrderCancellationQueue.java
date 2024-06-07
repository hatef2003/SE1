package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;

@Getter
public class OrderCancellationQueue {
    private final ArrayList<StopLimitOrder> deactivatedBuyOrders;
    private final ArrayList<StopLimitOrder> deactivatedSellOrders;

    OrderCancellationQueue() {
        deactivatedBuyOrders = new ArrayList<>();
        deactivatedSellOrders = new ArrayList<>();
    }

    public StopLimitOrder findStopLimitOrderById(long id) {
        Stream<StopLimitOrder> joinedDeactivatedList = Stream.concat(deactivatedBuyOrders.stream(),
                deactivatedSellOrders.stream());
        return joinedDeactivatedList.filter(order -> order.getOrderId() == id).findFirst().orElse(null);
    }

    public void addToDeactivatedBuy(StopLimitOrder newOrder) {
        deactivatedBuyOrders.add(newOrder);
        deactivatedBuyOrders.sort(Comparator.comparingDouble(StopLimitOrder::getStopLimit));
    }

    public void addToDeactivatedSell(StopLimitOrder newOrder) {
        deactivatedSellOrders.add(newOrder);
        deactivatedSellOrders.sort(Comparator.comparingDouble(StopLimitOrder::getStopLimit).reversed());
    }

    public void removeFromDeactivatedList(long id) {
        StopLimitOrder order = findStopLimitOrderById(id);
        if (order != null) {
            if (order.getSide() == Side.BUY)
                deactivatedBuyOrders.remove(order);
            else
                deactivatedSellOrders.remove(order);
        }
    }

    public ArrayList<StopLimitOrder> getActivatedOrder(int lastTradePrice) {
        ArrayList<StopLimitOrder> activatedList = new ArrayList<>();
        for (StopLimitOrder order : Stream.concat(deactivatedBuyOrders.stream(),
                deactivatedSellOrders.stream()).toList())
            if (order.isActive(lastTradePrice)) {
                activatedList.add(order);
                this.removeFromDeactivatedList(order.getOrderId());
            }
        return activatedList;
    }
}
