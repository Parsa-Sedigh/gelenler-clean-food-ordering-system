package com.food.ordering.system.kafka.producer.service.impl;

import com.food.ordering.system.kafka.producer.exception.KafkaProducerException;
import com.food.ordering.system.kafka.producer.service.KafkaProducer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.io.Serializable;

/* We keep the generic types as it is(the same types defined in KafkaProducer interface), so that we will be able to use this class
from any service with any avro model. So it will be a generic class. */
@Slf4j
@Component
public class KafkaProducerImpl<K extends Serializable, V extends SpecificRecordBase> implements KafkaProducer<K, V> {
    private final KafkaTemplate kafkaTemplate;

    public KafkaProducerImpl(KafkaTemplate kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /* Why do we use a callback here? Because the send() method on kafka producer is a non-blocking asynchronous call, so it won't
    return a result immediately. Instead, it requires a callback method to be called later asynchronously. */
    @Override
    public void send(String topicName, K key, V message, ListenableFutureCallback<SendResult<K, V>> callback) {
        log.info("Sending message={} to topic={}", message, topicName);

        try {
            ListenableFuture<SendResult<K, V>> kafkaResultFuture = (ListenableFuture<SendResult<K, V>>) kafkaTemplate.send(topicName, key, message);
            kafkaResultFuture.addCallback(callback);
        } catch (KafkaException e) {
            log.error("Error on kafka producer with key: {}, message: {} and exception: {}", key, message, e.getMessage());

            throw new KafkaProducerException("Error on kafka producer with key: " + key + " and message: " + message);
        }
    }

    /* This close() method will be called, when application is shutting down, thanks to @PreDestroy. So you can write cleanup code
    in a method with this annotation.*/
    @PreDestroy
    public void close() {
        if (kafkaTemplate != null) {
            log.info("Closing kafka producer!");

            kafkaTemplate.destroy();
        }
    }
}
