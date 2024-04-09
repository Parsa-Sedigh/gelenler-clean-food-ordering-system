package com.food.ordering.system.payment.service.messaging.listener.kafka;

import com.food.ordering.system.domain.event.payload.OrderPaymentEventPayload;
import com.food.ordering.system.kafka.consumer.KafkaConsumer;
import com.food.ordering.system.kafka.consumer.KafkaSingleItemConsumer;
import com.food.ordering.system.kafka.order.avro.model.PaymentOrderStatus;
import com.food.ordering.system.kafka.order.avro.model.PaymentRequestAvroModel;
import com.food.ordering.system.kafka.producer.KafkaMessageHelper;
import com.food.ordering.system.messaging.DebeziumOp;
import com.food.ordering.system.order.service.domain.event.OrderPaidEvent;
import com.food.ordering.system.payment.service.domain.exception.PaymentApplicationServiceException;
import com.food.ordering.system.payment.service.domain.exception.PaymentNotFoundException;
import com.food.ordering.system.payment.service.domain.ports.input.message.listener.PaymentRequestMessageListener;
import com.food.ordering.system.payment.service.messaging.mapper.PaymentMessagingDataMapper;
import debezium.payment.order_outbox.Envelope;
import debezium.payment.order_outbox.Value;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Slf4j
@Component
public class PaymentRequestKafkaListener implements KafkaSingleItemConsumer<Envelope> {
    private final PaymentRequestMessageListener paymentRequestMessageListener;
    private final PaymentMessagingDataMapper paymentMessagingDataMapper;
    private final KafkaMessageHelper kafkaMessageHelper;

    public PaymentRequestKafkaListener(PaymentRequestMessageListener paymentRequestMessageListener,
                                       PaymentMessagingDataMapper paymentMessagingDataMapper,
                                       KafkaMessageHelper kafkaMessageHelper) {
        this.paymentRequestMessageListener = paymentRequestMessageListener;
        this.paymentMessagingDataMapper = paymentMessagingDataMapper;
        this.kafkaMessageHelper = kafkaMessageHelper;
    }

    @Override
    @KafkaListener(id = "${kafka-consumer-config.payment-consumer-group-id}",
            topics = "${payment-service.payment-request-topic-name}")
    public void receive(@Payload Envelope message,
                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                        @Header(KafkaHeaders.OFFSET) Long offset) {


        /* Remember that this payment service can be called for two scenarios:
         - either for a pending order, just to complete a payment
         - or to cancel a payment, in case the order approval is failed in approval phase

         Note: We can't use optimistic locking as there is no records yet to update. So here, we catch DataAccessException.
         We want to only prevent retrying the unique constraint error.*/
//        messages.forEach(paymentRequestAvroModel -> {
        if (message.getBefore() == null && DebeziumOp.CREATE.getValue().equals(message.getOp())) {
            log.info("Incoming message in PaymentRequestKafkaListener: {} with key: {}, partition: {} and offset: {}",
                    message,
                    key,
                    partition,
                    offset);

            Value paymentRequestAvroModel = message.getAfter();
            OrderPaymentEventPayload orderPaymentEventPayload =
                    kafkaMessageHelper.getOrderEventPayload(paymentRequestAvroModel.getPayload(), OrderPaymentEventPayload.class);

            try {
                if (PaymentOrderStatus.PENDING.name().equals(orderPaymentEventPayload.getPaymentOrderStatus())) {
                    log.info("Processing payment for order id: {}", orderPaymentEventPayload.getOrderId());
                    paymentRequestMessageListener.completePayment(
                            paymentMessagingDataMapper.paymentRequestAvroModelToPaymentRequest(orderPaymentEventPayload, paymentRequestAvroModel));
                } else if (PaymentOrderStatus.CANCELLED.name().equals(orderPaymentEventPayload.getPaymentOrderStatus())) {
                    log.info("Cancelling payment for order id: {}", orderPaymentEventPayload.getOrderId());
                    paymentRequestMessageListener.cancelPayment(
                            paymentMessagingDataMapper.paymentRequestAvroModelToPaymentRequest(orderPaymentEventPayload, paymentRequestAvroModel));
                }
            } catch (DataAccessException e) {
                SQLException sqlException = (SQLException) e.getRootCause();

                // in case this is a unique violation error, we prevent retyping processing of this message
                if (sqlException != null && sqlException.getSQLState() != null &&
                        PSQLState.UNIQUE_VIOLATION.getState().equals(sqlException.getSQLState())) {
                    // NO-OP for unique constraint exception
                    log.error("Caught unique constraint exception with sql state: {} " +
                                    "in PaymentRequestKafkaListener for order id: {}",
                            sqlException.getSQLState(), orderPaymentEventPayload.getOrderId());
                } else {
                    /* The thrown exception wasn't a unique violation exception, so we want to actually retry the op that caused
                    this exception. Therefore we need to rethrow the exception because we just caught it!
                    With this, this kafka listener method can retry to process the msg.*/
                    throw new PaymentApplicationServiceException("Throwing DataAccessException in" +
                            " PaymentRequestKafkaListener: " + e.getMessage(), e);
                }
            } catch (PaymentNotFoundException e) {
                /* We can get PaymentNotFoundException from persistCancelPayment() . We don't want to propagate it, so we catch it here.
                With this, this kafka listener method won't retry to process a msg if there is no payment which shouldn't be
                the case for a correct saga flow.*/
                // NO-OP for PaymentNotFoundException
                log.error("No payment found for order id: {}", orderPaymentEventPayload.getOrderId());
            }
        }

    }
}
