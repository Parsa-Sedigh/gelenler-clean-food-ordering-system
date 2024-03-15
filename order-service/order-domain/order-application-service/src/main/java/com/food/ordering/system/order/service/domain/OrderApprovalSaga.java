package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.domain.event.EmptyEvent;
import com.food.ordering.system.domain.valueobject.OrderStatus;
import com.food.ordering.system.order.service.domain.OrderDomainService;
import com.food.ordering.system.order.service.domain.OrderSagaHelper;
import com.food.ordering.system.order.service.domain.dto.message.RestaurantApprovalResponse;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.event.OrderCancelledEvent;
//import com.food.ordering.system.order.service.domain.ports.output.message.publisher.payment.OrderCancelledPaymentRequestMessagePublisher;
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

@Slf4j
@Component
//public class OrderApprovalSaga implements SagaStep<RestaurantApprovalResponse, EmptyEvent, OrderCancelledEvent> {
public class OrderApprovalSaga implements SagaStep<RestaurantApprovalResponse> {
    private final OrderDomainService orderDomainService;
    private final OrderSagaHelper orderSagaHelper;
    //    private final OrderCancelledPaymentRequestMessagePublisher orderCancelledPaymentRequestMessagePublisher;
    private final PaymentOutboxHelper paymentOutboxHelper;
    private final ApprovalOutboxHelper approvalOutboxHelper;
    private final OrderDataMapper orderDataMapper;

    public OrderApprovalSaga(OrderDomainService orderDomainService,
                             OrderSagaHelper orderSagaHelper, PaymentOutboxHelper paymentOutboxHelper,
                             ApprovalOutboxHelper approvalOutboxHelper,
                             OrderDataMapper orderDataMapper
//                             OrderCancelledPaymentRequestMessagePublisher orderCancelledPaymentRequestMessagePublisher
    ) {
        this.orderDomainService = orderDomainService;
        this.orderSagaHelper = orderSagaHelper;
//        this.orderCancelledPaymentRequestMessagePublisher = orderCancelledPaymentRequestMessagePublisher;
        this.paymentOutboxHelper = paymentOutboxHelper;
        this.approvalOutboxHelper = approvalOutboxHelper;
        this.orderDataMapper = orderDataMapper;
    }

    @Override
    @Transactional
    public void process(RestaurantApprovalResponse restaurantApprovalResponse) {
        /* We used SagaStatus.PROCESSING because saga status is set to PROCESSING before triggering approval phase(this is done
        in process() method of OrderPaymentSaga).*/
        Optional<OrderApprovalOutboxMessage> orderApprovalOutboxMessageResponse =
                approvalOutboxHelper.getApprovalOutboxMessageBySagaIdAndSagaStatus(
                        UUID.fromString(restaurantApprovalResponse.getSagaId()),
                        SagaStatus.PROCESSING
                );

        /* This is because we could have duplicate messages come from kafka. So one of them won't be necessary to process. So we eliminate
        that message. */
        if (orderApprovalOutboxMessageResponse.isEmpty()) {
            log.info("An outbox message with saga id: {} is already processed!", restaurantApprovalResponse.getSagaId());

            return;
        }

        OrderApprovalOutboxMessage orderApprovalOutboxMessage = orderApprovalOutboxMessageResponse.get();

        Order order = approveOrder(restaurantApprovalResponse);

        SagaStatus sagaStatus = orderSagaHelper.orderStatusToSagaStatus(order.getOrderStatus());

        approvalOutboxHelper.save(getUpdatedApprovalOutboxMessage(orderApprovalOutboxMessage, order.getOrderStatus(), sagaStatus));
        paymentOutboxHelper.save(getUpdatedPaymentOutboxMessage(restaurantApprovalResponse.getSagaId(),
                order.getOrderStatus(), sagaStatus));

        log.info("Order with id: {} is approved", order.getId().getValue());

        /* Return an empty event because there will be no next step in the saga flow after approving the order. */
//        return EmptyEvent.INSTANCE;
    }

    @Override
    @Transactional
    public void rollback(RestaurantApprovalResponse restaurantApprovalResponse) {
        Optional<OrderApprovalOutboxMessage> orderApprovalOutboxMessageResponse = approvalOutboxHelper.getApprovalOutboxMessageBySagaIdAndSagaStatus(
                UUID.fromString(restaurantApprovalResponse.getSagaId()),
                SagaStatus.PROCESSING
        );

        /* As in process(), if this object is empty, it implies that the rollback op is already completed for this outbox message and
        the duplicate message has come after that.

        To prevent processing of duplicate messages, first we check if the message is already processed by reading the
        ApprovalOutboxMessage obj from db.

        This check will be executed if a thread for the same msg is already returned from this method which means it's changes are
        already committed. In that case, a second thread won't find an approval outbox message with PROCESSING saga status because
        we have set the saga status to COMPENSATING in the first thread.

        In case two threads of duplicate messages come to this method at the same time, we have optimistic locking to prevent
        race conditions and multiple identical updates. We have the @Version version field in ApprovalOutboxEntity which enables
        optimistic locking.*/
        if (orderApprovalOutboxMessageResponse.isEmpty()) {
            log.info("An outbox message with saga id: {} is already processed!", restaurantApprovalResponse.getSagaId());

            return;
        }

        OrderApprovalOutboxMessage orderApprovalOutboxMessage = orderApprovalOutboxMessageResponse.get();

        OrderCancelledEvent domainEvent = rollbackOrder(restaurantApprovalResponse);

        SagaStatus sagaStatus = orderSagaHelper.orderStatusToSagaStatus(domainEvent.getOrder().getOrderStatus());

        approvalOutboxHelper.save(getUpdatedApprovalOutboxMessage(
                orderApprovalOutboxMessage,
                domainEvent.getOrder().getOrderStatus(),
                sagaStatus
        ));

        /* Create a new payment outbox obj with the OrderCancelledEvent. Because here we got a rejected order from
         restaurant approval svc and we need to trigger an event for payment svc to rollback the payment changes.*/
        paymentOutboxHelper.savePaymentOutboxMessage(
                orderDataMapper.orderCancelledEventToOrderPaymentEventPayload(domainEvent),
                domainEvent.getOrder().getOrderStatus(),
                sagaStatus,
                OutboxStatus.STARTED,
                UUID.fromString(restaurantApprovalResponse.getSagaId())
        );

        log.info("Order with id: {} is cancelled", domainEvent.getOrder().getId().getValue());

//        return domainEvent;
    }

    private Order approveOrder(RestaurantApprovalResponse restaurantApprovalResponse) {
        log.info("Approving order with id: {}", restaurantApprovalResponse.getOrderId());

        Order order = orderSagaHelper.findOrder(restaurantApprovalResponse.getOrderId());

        orderDomainService.approveOrder(order);
        orderSagaHelper.saveOrder(order);

        return order;
    }

    private OrderApprovalOutboxMessage getUpdatedApprovalOutboxMessage(OrderApprovalOutboxMessage orderApprovalOutboxMessage,
                                                                       OrderStatus orderStatus,
                                                                       SagaStatus sagaStatus) {
        orderApprovalOutboxMessage.setProcessedAt(ZonedDateTime.now(ZoneId.of(UTC)));
        orderApprovalOutboxMessage.setOrderStatus(orderStatus);
        orderApprovalOutboxMessage.setSagaStatus(sagaStatus);

        return orderApprovalOutboxMessage;
    }

    private OrderPaymentOutboxMessage getUpdatedPaymentOutboxMessage(String sagaId,
                                                                     OrderStatus orderStatus,
                                                                     SagaStatus sagaStatus) {
        Optional<OrderPaymentOutboxMessage> orderPaymentOutboxMessageResponse = paymentOutboxHelper
                .getPaymentOutboxMessageBySagaIdAndSagaStatus(UUID.fromString(sagaId), SagaStatus.PROCESSING);

        if (orderPaymentOutboxMessageResponse.isEmpty()) {
            throw new OrderDomainException("Payment outbox message cannot be found in " +
                    SagaStatus.PROCESSING.name() + " state");
        }

        OrderPaymentOutboxMessage orderPaymentOutboxMessage = orderPaymentOutboxMessageResponse.get();
        orderPaymentOutboxMessage.setProcessedAt(ZonedDateTime.now(ZoneId.of(UTC)));
        orderPaymentOutboxMessage.setOrderStatus(orderStatus);
        orderPaymentOutboxMessage.setSagaStatus(sagaStatus);

        return orderPaymentOutboxMessage;
    }

    private OrderCancelledEvent rollbackOrder(RestaurantApprovalResponse restaurantApprovalResponse) {
        log.info("Cancelling order with id: {}", restaurantApprovalResponse.getOrderId());

        Order order = orderSagaHelper.findOrder(restaurantApprovalResponse.getOrderId());

        OrderCancelledEvent domainEvent = orderDomainService.cancelOrderPayment(order,
                restaurantApprovalResponse.getFailureMessages()
//                , orderCancelledPaymentRequestMessagePublisher
        );
        orderSagaHelper.saveOrder(order);

        return domainEvent;
    }
}
