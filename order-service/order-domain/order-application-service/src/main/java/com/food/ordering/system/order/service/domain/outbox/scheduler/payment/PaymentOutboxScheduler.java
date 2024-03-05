package com.food.ordering.system.order.service.domain.outbox.scheduler.payment;

import com.food.ordering.system.order.service.domain.outbox.model.payment.OrderPaymentOutboxMessage;
import com.food.ordering.system.order.service.domain.ports.output.message.publisher.payment.PaymentRequestMessagePublisher;
import com.food.ordering.system.outbox.OutboxScheduler;
import com.food.ordering.system.outbox.OutboxStatus;
import com.food.ordering.system.saga.SagaStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PaymentOutboxScheduler implements OutboxScheduler {
    private final PaymentOutboxHelper paymentOutboxHelper;
    private final PaymentRequestMessagePublisher paymentRequestMessagePublisher;

    public PaymentOutboxScheduler(PaymentOutboxHelper paymentOutboxHelper,
                                  PaymentRequestMessagePublisher paymentRequestMessagePublisher) {
        this.paymentOutboxHelper = paymentOutboxHelper;
        this.paymentRequestMessagePublisher = paymentRequestMessagePublisher;
    }

    /* Here, we pull the outbox table. Actually we will pull the domain events that are persisted into the outbox table.
    We want to get only outbox messages with outbox_status set as started and saga_status as started or compensating. */
    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${order-service.outbox-scheduler-fixed-rate}",
            initialDelayString = "${order-service.outbox-scheduler-initial-delay}")
    public void processOutboxMessage() {
        Optional<List<OrderPaymentOutboxMessage>> outboxMessagesResponse = paymentOutboxHelper.
                getPaymentOutboxMessageByOutboxStatusAndSagaStatus(OutboxStatus.STARTED,
                        SagaStatus.STARTED, SagaStatus.COMPENSATING);

        if (outboxMessagesResponse.isPresent() && outboxMessagesResponse.get().size() > 0) {
            List<OrderPaymentOutboxMessage> outboxMessages = outboxMessagesResponse.get();
            log.info("Received {} OrderPaymentOutboxMessage with ids: {}, sending to message bus!",
                    outboxMessages.size(),
                    outboxMessages.stream().map(outboxMessage -> outboxMessage.getId().toString()).collect(Collectors.joining(",")));

            /* We're passing a ref to updateOutboxStatus() method to the publish() method and we will be able to call the
             updateOutboxStatus() method with the completed or failed outbox status in the publish method implementation. The implementation
             is in messaging module.

             Note: When we pull the events from the outbox table and publish them successfully, we will mark them as completed or
             when it is failed, we'll mark them as failed in the updateOutboxStatus(). We do that in messaging module because only then we
             will know that if the publish ops are successful or not.

             When the OrderPaymentOutboxMessage is updated as completed, in the next schedule call of processOutboxMessage(),
             the getPaymentOutboxMessageByOutboxStatusAndSagaStatus() won't return the same outbox message. Because we are
             looking for OutboxStatus.STARTED. This way we prevent pulling the same event twice. However, depending on the response time
              of kafka cluster to producer, the updateOutboxStatus() may not be called before the second run of the scheduler method.
              So it means the same outbox message could be published more than once in some rare cases when the execution is
              delayed because of CPU decides to run some other threads or processes or because of a network delay from kafka cluster to
              producer. This can not be avoided with strict lock and wait implementations. But those types of locking will also slow down
              the app and is not acceptable in distributed apps. Because of that, we will also be cautious on the consumer side which is
              payment svc in this case and we will eliminate duplicate messages This will make sure to have idempotent messages.*/
            outboxMessages.forEach(outboxMessage -> paymentRequestMessagePublisher.publish(outboxMessage, this::updateOutboxStatus));

            log.info("{} OrderPaymentOutboxMessage sent to message bus!", outboxMessages.size());
        }
    }

    private void updateOutboxStatus(OrderPaymentOutboxMessage orderPaymentOutboxMessage, OutboxStatus outboxStatus) {
        orderPaymentOutboxMessage.setOutboxStatus(outboxStatus);

        /* Note: This method is called from processOutboxMessage() and maybe other methods in future. The processOutboxMessage() has
        @Transactional. We also have the @Transactional on paymentOutboxHelper.save() as well. We used @Transactional on
         paymentOutboxHelper.save() itself just to be safe(if it was called from somewhere else)*/
        paymentOutboxHelper.save(orderPaymentOutboxMessage);
        log.info("OrderPaymentOutboxMessage is updated with outbox status: {}", outboxStatus.name());
    }
}
