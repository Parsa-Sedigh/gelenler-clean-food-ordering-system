package com.food.ordering.system.domain.event;

/* Although functionally we won't use the generic type, it will help to mark an event object with the type of the entity
that will fire that event. For example, when we create the OrderCreatedEvent class, we will set this generic type as Order, which is
the entity that this event is originated from.*/
public interface DomainEvent<T> {

}
