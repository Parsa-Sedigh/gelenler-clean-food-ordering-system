package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.domain.event.EmptyEvent;
import com.food.ordering.system.domain.valueobject.OrderId;
import com.food.ordering.system.order.service.domain.dto.message.PaymentResponse;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.event.OrderPaidEvent;
import com.food.ordering.system.order.service.domain.exception.OrderNotFoundException;
import com.food.ordering.system.order.service.domain.ports.output.message.publisher.restaurantapproval.OrderPaidRestaurantRequestMessagePublisher;
import com.food.ordering.system.order.service.domain.ports.output.repository.OrderRepository;
import com.food.ordering.system.saga.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/* When a payment is completed, order svc will get a PaymentCompletedEvent and the OrderPaymentSaga should proceed to the next step which is
restaurant approval. The restaurant approval req should be triggered with a OrderPaidEvent(we send OrderPaidEvents to the
restaurant-approval-request-topic). That's why we set the return type of the process method OrderPaidEvent.
Note: For the type of rollback method, we use EmptyEvent because if the payment is not successful, we just need to
rollback the order svc's local DB operations and the saga flow will stop. Since we don't have any previous step before the order payment step.

We put @Transactional because we want to create a DB transaction and commit the changes in these methods. Then when we return the
DomainEvent to the caller which will be the PaymentResponseMessageListenerImpl, we will fire the events as the local DB transaction changes will
be already committed in that case.*/
@Slf4j
@Component
public class OrderPaymentSaga implements SagaStep<PaymentResponse, OrderPaidEvent, EmptyEvent> {
    private final OrderDomainService orderDomainService;
    private final OrderPaidRestaurantRequestMessagePublisher orderPaidRestaurantRequestMessagePublisher;
    private final OrderSagaHelper orderSagaHelper;

    public OrderPaymentSaga(OrderDomainService orderDomainService,
                            OrderPaidRestaurantRequestMessagePublisher orderPaidRestaurantRequestMessagePublisher,
                            OrderSagaHelper orderSagaHelper) {
        this.orderDomainService = orderDomainService;
        this.orderPaidRestaurantRequestMessagePublisher = orderPaidRestaurantRequestMessagePublisher;
        this.orderSagaHelper = orderSagaHelper;
    }

    @Override
    @Transactional
    public OrderPaidEvent process(PaymentResponse paymentResponse) {
        log.info("Completing payment for order with id: {}", paymentResponse.getOrderId());

        Order order = orderSagaHelper.findOrder(paymentResponse.getOrderId());
        OrderPaidEvent domainEvent = orderDomainService.payOrder(order, orderPaidRestaurantRequestMessagePublisher);
        orderSagaHelper.saveOrder(order);
        
        log.info("Order with id: {} is paid.", order.getId().getValue());

        return domainEvent;
    }

    /* In this case, this rollback() we don't need to return an event as there is no previous saga step to be triggered and because of that,
    we don't return an event from the cancelOrder() . */
    @Override
    @Transactional
    public EmptyEvent rollback(PaymentResponse paymentResponse) {
        log.info("Cancelling order with id: {}", paymentResponse.getOrderId());

        Order order = orderSagaHelper.findOrder(paymentResponse.getOrderId());
        orderDomainService.cancelOrder(order, paymentResponse.getFailureMessages()); // this method mutates the order
        orderSagaHelper.saveOrder(order);

        log.info("Order with id: {} is cancelled.", order.getId().getValue());

        return EmptyEvent.INSTANCE;
    }
}
