package com.food.ordering.system.domain.event.publisher;

import com.food.ordering.system.domain.event.DomainEvent;

public interface DomainEventPublisher<T extends DomainEvent> {
    // the implementation of this publish method will be in order-messaging-module
    void publish(T domainEvent);
}
