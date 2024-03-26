package com.food.ordering.system.payment.service.domain.event;

import com.food.ordering.system.domain.event.publisher.DomainEventPublisher;
import com.food.ordering.system.payment.service.domain.entity.Payment;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

public class PaymentCompletedEvent extends PaymentEvent{
//    private final DomainEventPublisher<PaymentCompletedEvent> paymentCompletedMessagePublisher;

    public PaymentCompletedEvent(Payment payment,
                                 ZonedDateTime createdAt
//                                 DomainEventPublisher<PaymentCompletedEvent> domainEventPublisher
    )
    {
        super(payment, createdAt, Collections.emptyList());
//        this.paymentCompletedMessagePublisher = domainEventPublisher;
    }

//    @Override
//    public void fire() {
//        /* publish requires a DomainEvent and we're currently inside a DomainEvent(this) object. Here, this refers to PaymentCompletedEvent object.*/
//        paymentCompletedMessagePublisher.publish(this);
//    }
}
