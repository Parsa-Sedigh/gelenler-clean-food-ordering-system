package com.food.ordering.system.payment.service.domain;

import com.food.ordering.system.payment.service.domain.dto.PaymentRequest;
import com.food.ordering.system.payment.service.domain.event.PaymentCancelledEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentCompletedEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentFailedEvent;
import com.food.ordering.system.payment.service.domain.ports.input.message.listener.PaymentRequestMessageListener;
//import com.food.ordering.system.payment.service.domain.ports.output.message.publisher.PaymentCancelledMessagePublisher;
//import com.food.ordering.system.payment.service.domain.ports.output.message.publisher.PaymentCompletedMessagePublisher;
//import com.food.ordering.system.payment.service.domain.ports.output.message.publisher.PaymentFailedMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PaymentRequestMessageListenerImpl implements PaymentRequestMessageListener {
    private final PaymentRequestHelper paymentRequestHelper;
//    private final PaymentCompletedMessagePublisher paymentCompletedMessagePublisher;
//    private final PaymentCancelledMessagePublisher paymentCancelledMessagePublisher;
//    private final PaymentFailedMessagePublisher paymentFailedMessagePublisher;

    public PaymentRequestMessageListenerImpl(PaymentRequestHelper paymentRequestHelper
//                                             PaymentCompletedMessagePublisher paymentCompletedMessagePublisher,
//                                             PaymentCancelledMessagePublisher paymentCancelledMessagePublisher,
//                                             PaymentFailedMessagePublisher paymentFailedMessagePublisher
    ) {
        this.paymentRequestHelper = paymentRequestHelper;
//        this.paymentCompletedMessagePublisher = paymentCompletedMessagePublisher;
//        this.paymentCancelledMessagePublisher = paymentCancelledMessagePublisher;
//        this.paymentFailedMessagePublisher = paymentFailedMessagePublisher;
    }


    @Override
    public void completePayment(PaymentRequest paymentRequest) {
//        PaymentEvent paymentEvent = paymentRequestHelper.persistPayment(paymentRequest);
//        fireEvent(paymentEvent);

        paymentRequestHelper.persistPayment(paymentRequest);
    }

    @Override
    public void cancelPayment(PaymentRequest paymentRequest) {
//        PaymentEvent paymentEvent = paymentRequestHelper.persistCancelPayment(paymentRequest);
//        fireEvent(paymentEvent);

        paymentRequestHelper.persistCancelPayment(paymentRequest);
    }

//    private void fireEvent(PaymentEvent paymentEvent) {
//        log.info("Publishing payment event with payment id: {} and order id: {}",
//                paymentEvent.getPayment().getId().getValue(),
//                paymentEvent.getPayment().getOrderId().getValue());

        /* To get rid of these else if blocks which would grow over time as we add more type of events, we define the fire() method
        in DomainEvent interface which is the common interface that all of these event classes implement. */
//        if (paymentEvent instanceof PaymentCompletedEvent) {
//            paymentCompletedMessagePublisher.publish((PaymentCompletedEvent) paymentEvent);
//        } else if (paymentEvent instanceof PaymentCancelledEvent) {
//            paymentCancelledMessagePublisher.publish((PaymentCancelledEvent) paymentEvent);
//        } else if (paymentEvent instanceof PaymentFailedEvent) {
//            paymentFailedMessagePublisher.publish((PaymentFailedEvent) paymentEvent);
//        }

//        paymentEvent.fire();
//    }
}
