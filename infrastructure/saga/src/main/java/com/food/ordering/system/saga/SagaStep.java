package com.food.ordering.system.saga;

import com.food.ordering.system.domain.event.DomainEvent;

/* Each saga step will process a type T and it returns a DomainEvent. In some saga steps, we won't need to fire an event if it is an
ending op. To handle these cases(that we won't fire a DomainEvent), create an empty event named EmptyEvent. */
//public interface SagaStep<T, S extends DomainEvent, U extends DomainEvent> {
public interface SagaStep<T> {
    // handles standard processing with a transaction
//    S process(T data);
    void process(T data);

    /* handles compensating transaction, in case a failure occurs in the next saga step. The idea is that if the next saga step fails,
     previous one should be able to rollback it's changes. */
//    U rollback(T data);
    void rollback(T data);
}
