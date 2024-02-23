package com.food.ordering.system.domain.event;

public final class EmptyEvent implements DomainEvent<Void> {
    /* Create a singleton instance of this class with a global instance constant*/
    public static final EmptyEvent INSTANCE = new EmptyEvent();

    /* Add a private constructor because this EmptyEvent is just a marker class and sharing the same instance among different classes is OK.*/
    private EmptyEvent() {}

    @Override
    public void fire() {

    }
}
