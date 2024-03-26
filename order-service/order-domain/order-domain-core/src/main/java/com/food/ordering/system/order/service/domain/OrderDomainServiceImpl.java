package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.domain.event.publisher.DomainEventPublisher;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.entity.Product;
import com.food.ordering.system.order.service.domain.entity.Restaurant;
import com.food.ordering.system.order.service.domain.event.OrderCancelledEvent;
import com.food.ordering.system.order.service.domain.event.OrderCreatedEvent;
import com.food.ordering.system.order.service.domain.event.OrderPaidEvent;
import com.food.ordering.system.order.service.domain.exception.OrderDomainException;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static com.food.ordering.system.domain.DomainConstants.UTC;

/* You see that the domain service uses multiple aggregates(root aggregates) to check some business
requirements(here, it uses Order and Restaurant aggregate roots). */
@Slf4j
public class OrderDomainServiceImpl implements OrderDomainService {
    @Override
    public OrderCreatedEvent validateAndInitiateOrder(Order order,
                                                      Restaurant restaurant
//                                                      DomainEventPublisher<OrderCreatedEvent> orderCreatedEventDomainEventPublisher
    ) {
        validateRestaurant(restaurant);
        setOrderProductInformation(order, restaurant);
        order.validateOrder();
        order.initializeOrder();

        log.info("Order with id: {} is initialized", order.getId().getValue());

//        return new OrderCreatedEvent(order, ZonedDateTime.now(ZoneId.of(UTC))
//                orderCreatedEventDomainEventPublisher
//        );

        return new OrderCreatedEvent(order, ZonedDateTime.now(ZoneId.of(UTC)));
    }

    @Override
    public OrderPaidEvent payOrder(Order order
//                                   DomainEventPublisher<OrderPaidEvent> orderPaidEventDomainEventPublisher
    ) {
        order.pay();

        log.info("Order with id: {} is paid", order.getId().getValue());

//        return new OrderPaidEvent(order, ZonedDateTime.now(ZoneId.of(UTC))
//                orderPaidEventDomainEventPublisher
//        );

        return new OrderPaidEvent(order, ZonedDateTime.now(ZoneId.of(UTC)));
    }

    /* This method doesn't return an event because it's a possible last step in order processing and after approval, we don't need to
    fire an event. Instead, the client will fetch the data using the get endpoint with tracking id.
    If you will the client that is capable of capturing events, you can fire a final event even here that will be consumed by the
    client to continue with the delivery process. However, we won't implement the client as an event consumer, instead,
    we will use a http client(postman).*/
    @Override
    public void approveOrder(Order order) {
        order.approve();

        log.info("Order with id: {} is approved", order.getId().getValue());
    }

    @Override
    public OrderCancelledEvent cancelOrderPayment(Order order,
                                                  List<String> failureMessages
//                                                  DomainEventPublisher<OrderCancelledEvent> orderCancelledEventDomainEventPublisher
    ) {
        order.initCancel(failureMessages);

        log.info("Order payment is cancelling for order id: {}", order.getId().getValue());

        return new OrderCancelledEvent(order, ZonedDateTime.now(ZoneId.of(UTC))
//                orderCancelledEventDomainEventPublisher
        );
    }

    /* This is a possible last step, so we don't return an event(although you could, if the client is consuming the event returned here). */
    @Override
    public void cancelOrder(Order order, List<String> failureMessages) {
        order.cancel(failureMessages);

        log.info("Order with id: {} is cancelled", order.getId().getValue());
    }

    private void validateRestaurant(Restaurant restaurant) {
        if (!restaurant.isActive()) {
            throw new OrderDomainException("Restaurant with id " + restaurant.getId().getValue() + " is currently not active!");
        }
    }

    private void setOrderProductInformation(Order order, Restaurant restaurant) {
        /* Note: By using a hashmap for products, we can reduce the time complexity to O(n) with a small space cost. */
        // find the corresponding product of order item, in the restaurant products
        order.getItems().forEach(orderItem -> restaurant.getProducts().forEach(restaurantProduct -> {
            Product currentProduct = orderItem.getProduct();

            if (currentProduct.equals(restaurantProduct)) {
                currentProduct.updateWithConfirmedNameAndPrice(restaurantProduct.getName(), restaurantProduct.getPrice());
            }
        }));
    }
}
