package com.food.ordering.system.order.service.messaging.listener.kafka;

import com.food.ordering.system.domain.event.payload.PaymentOrderEventPayload;
import com.food.ordering.system.kafka.consumer.KafkaConsumer;
import com.food.ordering.system.kafka.order.avro.model.PaymentStatus;
import com.food.ordering.system.kafka.producer.KafkaMessageHelper;
import com.food.ordering.system.messaging.DebeziumOp;
import com.food.ordering.system.order.service.domain.exception.OrderDomainException;
import com.food.ordering.system.order.service.domain.ports.input.message.listener.payment.PaymentResponseMessageListener;
import com.food.ordering.system.order.service.messaging.mapper.OrderMessagingDataMapper;
import debezium.payment.order_outbox.Envelope;
import debezium.payment.order_outbox.Value;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

@Slf4j
@Component
public class PaymentResponseKafkaListener implements KafkaConsumer<Envelope> {
    private final PaymentResponseMessageListener paymentResponseMessageListener;
    private final OrderMessagingDataMapper orderMessagingDataMapper;
    private final KafkaMessageHelper kafkaMessageHelper;

    public PaymentResponseKafkaListener(PaymentResponseMessageListener paymentResponseMessageListener,
                                        OrderMessagingDataMapper orderMessagingDataMapper,
                                        KafkaMessageHelper kafkaMessageHelper) {
        this.paymentResponseMessageListener = paymentResponseMessageListener;
        this.orderMessagingDataMapper = orderMessagingDataMapper;
        this.kafkaMessageHelper = kafkaMessageHelper;
    }

    /* Kafka consumer offsets are only committed after returning from this receive() method that is annotated with @KafkaListener. */
    @Override
    @KafkaListener(id = "${kafka-consumer-config.payment-consumer-group-id}", topics = "${order-service.payment-response-topic-name}")
    public void receive(@Payload List<Envelope> messages,
                        @Header(KafkaHeaders.RECEIVED_KEY) List<String> keys,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
                        @Header(KafkaHeaders.OFFSET) List<Long> offsets) {
        log.info("{} number of payment responses received!",
                messages.stream().filter(message ->
                        message.getBefore() == null && DebeziumOp.CREATE.name().equals(message.getOp())
                ).toList().size());

        messages.forEach(avroModel -> {
            if (avroModel.getBefore() == null && DebeziumOp.CREATE.name().equals(avroModel.getOp())) {
                log.info("Incoming message in PaymentResponseKafkaListener: {}", avroModel);

                Value paymentResponseAvroModel = avroModel.getAfter();
                PaymentOrderEventPayload paymentOrderEventPayload =
                        kafkaMessageHelper.getOrderEventPayload(paymentResponseAvroModel.getPayload(), PaymentOrderEventPayload.class);

                try {
                    if (PaymentStatus.COMPLETED.name().equals(paymentOrderEventPayload.getPaymentStatus())) {
                        log.info("Processing successful payment for order id: {}", paymentOrderEventPayload.getOrderId());

                        paymentResponseMessageListener.paymentCompleted(
                                orderMessagingDataMapper.paymentResponseAvroModelToPaymentResponse(paymentOrderEventPayload, paymentResponseAvroModel)
                        );
                    } else if (PaymentStatus.CANCELLED.name().equals(paymentOrderEventPayload.getPaymentStatus()) ||
                            PaymentStatus.FAILED.name().equals(paymentOrderEventPayload.getPaymentStatus())) {
                        log.info("Processing unsuccessful payment for order id: {}", paymentOrderEventPayload.getOrderId());

                        paymentResponseMessageListener.paymentCancelled(
                                orderMessagingDataMapper.paymentResponseAvroModelToPaymentResponse(paymentOrderEventPayload, paymentResponseAvroModel)
                        );
                    }
                } catch (OptimisticLockingFailureException e) {
                    /* we have enabled optimistic locking and it can be thrown from the OrderPaymentSaga's process() or rollback(). */
                    // NO-OP for optimistic locking. This means another thread finished the work for the same message, do not throw error to
                    // prevent reading the data from kafka again!
                    log.error("Caught optimistic locking exception in PaymentResponseKafkaListener for order id: {}",
                            paymentOrderEventPayload.getOrderId());
                } catch (OrderDomainException e) {
                    /* If OrderDomainException is thrown which is caused by order couldn't be found, then saga cannot continue. Therefore,
                    we shouldn't retry the op. So we also catch this exception here and make it a no-op instead of propagating
                    the exception.We don't do anything about it, just logging.*/
                    // NO-OP for OrderDomainException
                    log.error("No order found for order id: {}", paymentOrderEventPayload.getOrderId());
                } catch (DataAccessException e) {
                    /* By catching unique constraint exception and not doing anything, we will make sure the same msg will not be
                    read again and again because the offsets will be committed after returning from the receive() method when no exception
                    is thrown, because kafka consumer offsets are only committed after returning from this receive() method(so no exception
                    should be returned), so the offset will be committed to kafka for all the records in the input list only after returning
                    from this method. And if we got an exception and do not handle it here for any record, spring will keep committing the
                    offsets for old items in the list. This will cause to read the same items again in the next poll from kafka consumer.
                    So in failure case, it won't commit offsets for any of the items in the input list and if this happens in the
                    forEach loop after processing some records, that processed records won't be committed to kafka and will need to be
                    re-processed.

                    Another option for solving this, is to use a listener with SINGLE item instead of a list. This decision depends on
                    possibility of getting an exception in processing the lists. If there is less chance of getting an exception,
                    it's better to keep a list of inputs for better throughput, however if you keep getting lots of exceptions,
                    then it'll be costly to re-process and get unique constraint exception for the msgs and eliminating them.

                    Here, we keep accepting the list of msgs and catch the exception because we don't see high chance of
                    getting exception here.

                    In PaymentKafkaListener, there's a scenario where the other opt(getting a single item in the listener) will make
                    more sense.*/
                    SQLException sqlException = (SQLException) e.getRootCause();

                    // in case this is a unique violation error, we prevent retyping processing of this message
                    if (sqlException != null && sqlException.getSQLState() != null &&
                            PSQLState.UNIQUE_VIOLATION.getState().equals(sqlException.getSQLState())) {
                        // NO-OP for unique constraint exception
                        log.error("Caught unique constraint exception with sql state: {} " +
                                        "in PaymentResponseKafkaListener for order id: {}",
                                sqlException.getSQLState(), paymentOrderEventPayload.getOrderId());
                    }
                }

                /* What about other errors? What will happen when some other exception is thrown during saga operation?
                Since we don't catch those other exceptions, the exception will be propagated and spring will assume that
                the event listener method call is failed and then it will read the same message again from kafka. So it does the retry for
                messages that were failed to process, automatically. But for OptimisticLockingFailureException we don't want this behavior(we don't
                want to retry the message that we failed to process) so we catch that exception and prevent propagation of that exception.
                Because it was already processed by another thread.*/
            }
        });
    }
}
