//package com.food.ordering.system.order.service.domain;
//
//import com.food.ordering.system.order.service.domain.event.OrderCreatedEvent;
//import com.food.ordering.system.order.service.domain.ports.output.message.publisher.payment.OrderCreatedPaymentRequestMessagePublisher;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.event.TransactionalEventListener;
//
//////////////// If we used spring's event publishing capabilities //////////////
//@Slf4j
//@Component
//public class OrderCreatedEventApplicationListener {
//    private final OrderCreatedPaymentRequestMessagePublisher orderCreatedPaymentRequestMessagePublisher;
//
//    public OrderCreatedEventApplicationListener(OrderCreatedPaymentRequestMessagePublisher orderCreatedPaymentRequestMessagePublisher) {
//        this.orderCreatedPaymentRequestMessagePublisher = orderCreatedPaymentRequestMessagePublisher;
//    }
//
//    /* With @TransactionalEventListener, this method will only be called when the method that publishes the ApplicationEventPublisher,
//    is completed and the transaction is committed(when the method that calls ApplicationEventPublisher.publishEvent() is done). */
//    @TransactionalEventListener
//    void process(OrderCreatedEvent orderCreatedEvent) {
//        orderCreatedPaymentRequestMessagePublisher.publish(orderCreatedEvent);
//    }
//}
