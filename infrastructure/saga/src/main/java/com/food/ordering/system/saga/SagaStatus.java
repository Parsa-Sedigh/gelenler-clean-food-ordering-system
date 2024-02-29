package com.food.ordering.system.saga;

/* SagaStatus is used to hold the saga status in the outbox tables. */
public enum SagaStatus {
    STARTED, FAILED, SUCCEEDED, PROCESSING, COMPENSATING, COMPENSATED
}
