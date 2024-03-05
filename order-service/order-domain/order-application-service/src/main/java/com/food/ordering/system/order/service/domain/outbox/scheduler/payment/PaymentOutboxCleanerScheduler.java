package com.food.ordering.system.order.service.domain.outbox.scheduler.payment;

import com.food.ordering.system.order.service.domain.outbox.model.payment.OrderPaymentOutboxMessage;
import com.food.ordering.system.outbox.OutboxScheduler;
import com.food.ordering.system.outbox.OutboxStatus;
import com.food.ordering.system.saga.SagaStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/* Note: Before deleting the completed outbox events of order payment outbox table, you might also create an archive table and move the data
there. Then you can use that table along with the logs for analyzing the system. */
@Slf4j
@Component
public class PaymentOutboxCleanerScheduler implements OutboxScheduler {
    private final PaymentOutboxHelper paymentOutboxHelper;

    public PaymentOutboxCleanerScheduler(PaymentOutboxHelper paymentOutboxHelper) {
        this.paymentOutboxHelper = paymentOutboxHelper;
    }

    @Override
    @Scheduled(cron = "@midnight")
    public void processOutboxMessage() {
        /* We want to double-check that the SagaStatus of the messages is one of the ending states(succeeded, failed, compensated) and not a
        starting or intermediate state like STARTED, PROCESSING or COMPENSATING. */
        Optional<List<OrderPaymentOutboxMessage>> outboxMessagesResponse =
                paymentOutboxHelper.getPaymentOutboxMessageByOutboxStatusAndSagaStatus(
                        OutboxStatus.COMPLETED,
                        SagaStatus.SUCCEEDED,
                        SagaStatus.FAILED,
                        SagaStatus.COMPENSATED
                );

        if (outboxMessagesResponse.isPresent()) {
            List<OrderPaymentOutboxMessage> outboxMessages = outboxMessagesResponse.get();

            log.info("Received {} OrderPaymentOutboxMessage for clean-up. The payloads: {}",
                    outboxMessages.size(),
                    outboxMessages.stream().map(OrderPaymentOutboxMessage::getPayload).collect(Collectors.joining("\n")));

            paymentOutboxHelper.deletePaymentOutboxMessageByOutboxStatusAndSagaStatus(
                    OutboxStatus.COMPLETED,
                    SagaStatus.SUCCEEDED,
                    SagaStatus.FAILED,
                    SagaStatus.COMPENSATED);

            log.info("{} OrderPaymentOutboxMessage deleted!", outboxMessages.size());
        }
    }
}
