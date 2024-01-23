package com.food.ordering.system.order.service.domain.ports.input.message.listener.payment;

import com.food.ordering.system.order.service.domain.dto.message.PaymentResponse;

public interface PaymentResponseMessageListener {
    void paymentCompleted(PaymentResponse paymentResponse);

    /* This method can be called in case a payment is failed because of a business logic invariant. But it can be a response
    to the payment cancel request as part of the saga rollback operation.*/
    void paymentCancelled(PaymentResponse paymentResponse);
}
