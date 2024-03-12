package com.food.ordering.system.order.service.domain.event;

import com.food.ordering.system.domain.event.DomainEvent;
import com.food.ordering.system.domain.event.publisher.DomainEventPublisher;
import com.food.ordering.system.order.service.domain.entity.Order;

import java.time.ZonedDateTime;

/* We don't create a builder for this class(an event object) as it has only two fields. */
public class OrderCreatedEvent extends OrderEvent {
//    private final DomainEventPublisher<OrderCreatedEvent> orderCreatedEventDomainEventPublisher;

    public OrderCreatedEvent(Order order,
                             ZonedDateTime createdAt,
                             DomainEventPublisher<OrderCreatedEvent> orderCreatedEventDomainEventPublisher) {
        super(order, createdAt);
//        this.orderCreatedEventDomainEventPublisher = orderCreatedEventDomainEventPublisher;
    }

//    @Override
//    public void fire() {
//        orderCreatedEventDomainEventPublisher.publish(this);
//    }
}
