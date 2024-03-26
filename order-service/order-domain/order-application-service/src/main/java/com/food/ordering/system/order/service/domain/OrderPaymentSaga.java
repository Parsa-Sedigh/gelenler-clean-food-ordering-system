package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.domain.valueobject.OrderStatus;
import com.food.ordering.system.domain.valueobject.PaymentStatus;
import com.food.ordering.system.order.service.domain.dto.message.PaymentResponse;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.event.OrderPaidEvent;
import com.food.ordering.system.order.service.domain.exception.OrderDomainException;
import com.food.ordering.system.order.service.domain.mapper.OrderDataMapper;
import com.food.ordering.system.order.service.domain.outbox.model.approval.OrderApprovalOutboxMessage;
import com.food.ordering.system.order.service.domain.outbox.model.payment.OrderPaymentOutboxMessage;
import com.food.ordering.system.order.service.domain.outbox.scheduler.approval.ApprovalOutboxHelper;
import com.food.ordering.system.order.service.domain.outbox.scheduler.payment.PaymentOutboxHelper;
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

    /* Note: This method contains concurrent access and requires more attention:
    If two exact messages with same saga id was added to kafka topic, first one comes to this process() method and it will only
    commit the outbox table updates when this method returns. If the second thread comes after the first thread returns from the process()
    method, we are safe and our validation check of saga status(should be STARTED) at the start of the process() method will work. Because
    the previous thread committed the changes. So the second thread will not be able to find a saga item with STARTED status and
    return immediately.

    However, things won't be that easy every time. The second thread could come before the first thread returns from the process() method.
    In that case, the second thread will find the same saga id with STARTED status. In this case, the process() method will
    run more than once with multiple threads possibly at the same time.

    One way to prevent this, is to synchronize the process() method but it will affect the perf badly as it will only allow running a
    single thread at a time.

    Instead of this, we use optimistic locking here and it is actually already implemented. In OrderPaymentOutboxMessage, we have a
    `version` field. Then in PaymentOutboxEntity class, we have `@Version private int version;`. The @Version enables optimistic locking for
    that JPA entity.*/
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
        this case before processing data. */
        if (orderPaymentOutboxMessageResponse.isEmpty()) {
            log.info("An outbox message with id: {} is already processed!", paymentResponse.getSagaId());

            return;
        }

        OrderPaymentOutboxMessage orderPaymentOutboxMessage = orderPaymentOutboxMessageResponse.get();

        OrderPaidEvent domainEvent = completePaymentForOrder(paymentResponse);

        /* if we update the saga status here, the next thread that uses duplicate saga message, won't process the data. */
        SagaStatus sagaStatus = orderSagaHelper.orderStatusToSagaStatus(domainEvent.getOrder().getOrderStatus());

        paymentOutboxHelper.save(getUpdatedPaymentOutboxMessage(
                orderPaymentOutboxMessage,
                domainEvent.getOrder().getOrderStatus(),
                sagaStatus));

        /* There is an INSERT op here and there is a unique index on restaurant_approval_outbox table on columns: type, saga_id, saga_status.
        So when a second tx tries to create the same record, it will get a unique constraint exception which is another check after the
        optimistic locking. However in this method, we won't need this check because optimistic locking will do the job.*/
        approvalOutboxHelper.saveApprovalOutboxMessage(orderDataMapper.orderPaidEventToOrderApprovalEventPayload(domainEvent),
                domainEvent.getOrder().getOrderStatus(),
                sagaStatus,
                OutboxStatus.STARTED,
                UUID.fromString(paymentResponse.getSagaId()));

        log.info("Order with id: {} is paid.", domainEvent.getOrder().getId().getValue());
    }

    /* In this case, this rollback() we don't need to return an event as there is no previous saga step to be triggered and because of that,
    we don't return an event from the cancelOrder() . */
    @Override
    @Transactional
    public void rollback(PaymentResponse paymentResponse) {
        Optional<OrderPaymentOutboxMessage> orderPaymentOutboxMessageResponse = paymentOutboxHelper.
                getPaymentOutboxMessageBySagaIdAndSagaStatus(
                        UUID.fromString(paymentResponse.getSagaId()),
                        getCurrentSagaStatus(paymentResponse.getPaymentStatus()));

        /* If we get into this if block, it means that the rollback is already done and this rollback() method is called twice with a
        duplicate message. */
        if (orderPaymentOutboxMessageResponse.isEmpty()) {
            log.info("An outbox message with saga id: {} is already roll backed!", paymentResponse.getSagaId());

            return;
        }

        OrderPaymentOutboxMessage orderPaymentOutboxMessage = orderPaymentOutboxMessageResponse.get();

        Order order = rollbackPaymentForOrder(paymentResponse);

        SagaStatus sagaStatus = orderSagaHelper.orderStatusToSagaStatus(order.getOrderStatus());

        paymentOutboxHelper.save(getUpdatedPaymentOutboxMessage(
                orderPaymentOutboxMessage,
                order.getOrderStatus(),
                sagaStatus
        ));

        /* Note: If payment status is CANCELLED, we MUST have an order approval outbox record with COMPENSATING saga state, otherwise,
         getUpdateApprovalOutboxMessage() will throw an exception.*/
        if (paymentResponse.getPaymentStatus() == PaymentStatus.CANCELLED) {
            approvalOutboxHelper.save(getUpdateApprovalOutboxMessage(
                    paymentResponse.getSagaId(),
                    order.getOrderStatus(),
                    sagaStatus
            ));
        }

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

    private OrderPaidEvent completePaymentForOrder(PaymentResponse paymentResponse) {
        log.info("Completing payment for order with id: {}", paymentResponse.getOrderId());

        Order order = orderSagaHelper.findOrder(paymentResponse.getOrderId());

//        OrderPaidEvent domainEvent = orderDomainService.payOrder(order
////                orderPaidRestaurantRequestMessagePublisher
//        );

        OrderPaidEvent domainEvent = orderDomainService.payOrder(order);

        orderSagaHelper.saveOrder(order); // todo: orderRepository.save(order)?

        return domainEvent;
    }

    private SagaStatus[] getCurrentSagaStatus(PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            /* When we trigger the payment svc for the first time, saga is just STARTED and when the PaymentCompletedEvent
            returned from the payment service, we still have the saga in STARTED state*/
            case COMPLETED -> new SagaStatus[]{SagaStatus.STARTED};

            /* When we call payment svc to cancel and rollback a payment, we're in the middle of saga processing and state of saga is
            PROCESSING. */
            case CANCELLED -> new SagaStatus[]{SagaStatus.PROCESSING};

            /* In the payment svc, payment status can be set as FAILED in both payment complete and payment cancelled reqs. */
            case FAILED -> new SagaStatus[]{SagaStatus.STARTED, SagaStatus.PROCESSING};
        };
    }

    private Order rollbackPaymentForOrder(PaymentResponse paymentResponse) {
        log.info("Cancelling order with id: {}", paymentResponse.getOrderId());

        Order order = orderSagaHelper.findOrder(paymentResponse.getOrderId());
        orderDomainService.cancelOrder(order, paymentResponse.getFailureMessages()); // this method mutates the order
        orderSagaHelper.saveOrder(order);

        return order;
    }

    private OrderApprovalOutboxMessage getUpdateApprovalOutboxMessage(String sagaId, OrderStatus orderStatus, SagaStatus sagaStatus) {
        Optional<OrderApprovalOutboxMessage> orderApprovalOutboxMessageResponse =
                approvalOutboxHelper.getApprovalOutboxMessageBySagaIdAndSagaStatus(
                        UUID.fromString(sagaId),
                        SagaStatus.COMPENSATING);

        if (orderApprovalOutboxMessageResponse.isEmpty()) {
            throw new OrderDomainException("Approval outbox message could not be found in " +
                    SagaStatus.COMPENSATING.name() + " status!");
        }

        OrderApprovalOutboxMessage orderApprovalOutboxMessage = orderApprovalOutboxMessageResponse.get();

        orderApprovalOutboxMessage.setProcessedAt(ZonedDateTime.now(ZoneId.of(UTC)));
        orderApprovalOutboxMessage.setOrderStatus(orderStatus);
        orderApprovalOutboxMessage.setSagaStatus(sagaStatus);

        return orderApprovalOutboxMessage;
    }
}
