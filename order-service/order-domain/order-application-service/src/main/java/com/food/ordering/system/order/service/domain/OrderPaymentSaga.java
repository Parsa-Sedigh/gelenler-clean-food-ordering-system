package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.domain.event.EmptyEvent;
import com.food.ordering.system.domain.valueobject.OrderId;
import com.food.ordering.system.domain.valueobject.OrderStatus;
import com.food.ordering.system.order.service.domain.dto.message.PaymentResponse;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.event.OrderPaidEvent;
import com.food.ordering.system.order.service.domain.exception.OrderNotFoundException;
import com.food.ordering.system.order.service.domain.mapper.OrderDataMapper;
import com.food.ordering.system.order.service.domain.outbox.model.payment.OrderPaymentOutboxMessage;
import com.food.ordering.system.order.service.domain.outbox.scheduler.approval.ApprovalOutboxHelper;
import com.food.ordering.system.order.service.domain.outbox.scheduler.payment.PaymentOutboxHelper;
import com.food.ordering.system.order.service.domain.ports.output.message.publisher.restaurantapproval.OrderPaidRestaurantRequestMessagePublisher;
import com.food.ordering.system.order.service.domain.ports.output.repository.OrderRepository;
import com.food.ordering.system.outbox.OutboxStatus;
import com.food.ordering.system.saga.SagaStatus;
import com.food.ordering.system.saga.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.food.ordering.system.domain.DomainConstants.UTC;

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
public class OrderPaymentSaga implements SagaStep<PaymentResponse> {
    private final OrderDomainService orderDomainService;
//    private final OrderPaidRestaurantRequestMessagePublisher orderPaidRestaurantRequestMessagePublisher;
    private final OrderSagaHelper orderSagaHelper;
    private final PaymentOutboxHelper paymentOutboxHelper;
    private final ApprovalOutboxHelper approvalOutboxHelper;
    private final OrderDataMapper orderDataMapper;

    public OrderPaymentSaga(OrderDomainService orderDomainService,
//                            OrderPaidRestaurantRequestMessagePublisher orderPaidRestaurantRequestMessagePublisher,
                            OrderSagaHelper orderSagaHelper,
                            PaymentOutboxHelper paymentOutboxHelper,
                            ApprovalOutboxHelper approvalOutboxHelper,
                            OrderDataMapper orderDataMapper) {
        this.orderDomainService = orderDomainService;
//        this.orderPaidRestaurantRequestMessagePublisher = orderPaidRestaurantRequestMessagePublisher;
        this.orderSagaHelper = orderSagaHelper;
        this.paymentOutboxHelper = paymentOutboxHelper;
        this.approvalOutboxHelper = approvalOutboxHelper;
        this.orderDataMapper = orderDataMapper;
    }

    @Override
    @Transactional
    public void process(PaymentResponse paymentResponse) {
        Optional<OrderPaymentOutboxMessage> orderPaymentOutboxMessageResponse = paymentOutboxHelper.getPaymentOutboxMessageBySagaIdAndSagaStatus(
                UUID.fromString(paymentResponse.getSagaId()),
                SagaStatus.STARTED
        );

        /* When this message can be empty?
        If the same message is produced twice on payment_response kafka topic from the payment svc, then the PaymentResponseMessageListenerImpl will be
        called twice for the same message. PaymentResponseMessageListenerImpl is called from kafka listener in messaging module.
        Then the first message will find the outbox message record with STARTED saga status and then it will process and update
        the saga status according to the saga flow.
        In that case, the second message that comes from kafka won't find the record with the specified sagaId and STARTED saga status.
        Because it will be updated when the first message is processed.

        So this is one of the edge cases that we need to cover and we do that by checking the outbox record with the
        saga status(SagaStatus.STARTED) at the start of the process() method.

        Q: When can payment svc create two messages with the same saga id?

        A: It could happen when order svc sends the same outbox message to the payment svc and this can happen if the scheduler method runs more than
        once before the outbox message set as COMPLETED.

        Another scenario could be when you have multiple instances of the order svc, the same message can be sent twice from each instance to
        the kafka topic. In the end, in the distributed arch, sending the messages multiple times could be possible. So we need to take care of
        this case before processing data.*/
        if (orderPaymentOutboxMessageResponse.isEmpty()) {
            log.info("An outbox message with id: {} is already processed!", paymentResponse.getSagaId());

            return;
        }

        OrderPaymentOutboxMessage orderPaymentOutboxMessage = orderPaymentOutboxMessageResponse.get();

        log.info("Completing payment for order with id: {}", paymentResponse.getOrderId());

        Order order = orderSagaHelper.findOrder(paymentResponse.getOrderId());
        OrderPaidEvent domainEvent = orderDomainService.payOrder(order
//                orderPaidRestaurantRequestMessagePublisher
        );

        orderSagaHelper.saveOrder(order); // todo: orderRepository.save(order)?

        /* if we update the saga status here, the next thread that uses duplicate saga message, won't process the data. */
        SagaStatus sagaStatus = orderSagaHelper.orderStatusToSagaStatus(domainEvent.getOrder().getOrderStatus());

        paymentOutboxHelper.save(getUpdatedPaymentOutboxMessage(
                orderPaymentOutboxMessage,
                domainEvent.getOrder().getOrderStatus(),
                sagaStatus));

        approvalOutboxHelper.saveApprovalOutboxMessage(orderDataMapper.orderPaidEventToOrderApprovalEventPayload(domainEvent),
                order.getOrderStatus(),
                sagaStatus,
                OutboxStatus.STARTED,
                UUID.fromString(paymentResponse.getSagaId()));
        
        log.info("Order with id: {} is paid.", order.getId().getValue());
    }

    /* In this case, this rollback() we don't need to return an event as there is no previous saga step to be triggered and because of that,
    we don't return an event from the cancelOrder() . */
    @Override
    @Transactional
    public void rollback(PaymentResponse paymentResponse) {
        log.info("Cancelling order with id: {}", paymentResponse.getOrderId());

        Order order = orderSagaHelper.findOrder(paymentResponse.getOrderId());
        orderDomainService.cancelOrder(order, paymentResponse.getFailureMessages()); // this method mutates the order
        orderSagaHelper.saveOrder(order);

        log.info("Order with id: {} is cancelled.", order.getId().getValue());

//        return EmptyEvent.INSTANCE;
    }

    private OrderPaymentOutboxMessage getUpdatedPaymentOutboxMessage(OrderPaymentOutboxMessage orderPaymentOutboxMessage,
                                                                     OrderStatus orderStatus,
                                                                     SagaStatus sagaStatus) {
        orderPaymentOutboxMessage.setProcessedAt(ZonedDateTime.now(ZoneId.of(UTC)));
        orderPaymentOutboxMessage.setOrderStatus(orderStatus);
        orderPaymentOutboxMessage.setSagaStatus(sagaStatus);

        /* Since we sent the OrderPaymentOutboxMessage as a reference, even without returning anything, it will be updated on the caller. However,
        to make the called code more fluent, we return the object and use it on the caller. */
        return orderPaymentOutboxMessage;
    }
}
