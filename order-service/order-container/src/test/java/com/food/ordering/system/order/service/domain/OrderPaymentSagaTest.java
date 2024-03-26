package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.order.service.dataaccess.outbox.payment.entity.PaymentOutboxEntity;
import com.food.ordering.system.order.service.dataaccess.outbox.payment.repository.PaymentOutboxJpaRepository;
import com.food.ordering.system.order.service.domain.dto.message.PaymentResponse;
import com.food.ordering.system.saga.SagaStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static com.food.ordering.system.saga.order.SagaConstants.ORDER_SAGA_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

/* With this @SpringBootTest pointing to the OrderServiceApplication which is the main class of the order-service, we aim to
start the spring boot context in our test. Like we're starting the app.

OrderPaymentSagaTestSetup.sql uses the default executionPhase which is BEFORE_TEST_METHOD. So it will be executed before each test method.
But the OrderPaymentSagaTestCleanUp.sql uses: executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD , which means
the given sql will be executed after each test method.*/
@Slf4j
@SpringBootTest(classes = OrderServiceApplication.class)
@Sql(value = {"classpath:sql/OrderPaymentSagaTestSetup.sql"})
@Sql(value = {"classpath:sql/OrderPaymentSagaTestCleanUp.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class OrderPaymentSagaTest {
    @Autowired
    private OrderPaymentSaga orderPaymentSaga;

    @Autowired
    private PaymentOutboxJpaRepository paymentOutboxJpaRepository;

    private final UUID SAGA_ID = UUID.fromString("15a497c1-0f4b-4eff-b9f4-c402c8c07afa");
    private final UUID ORDER_ID = UUID.fromString("d215b5f8-0249-4dc5-89a3-51fd148cfb17");
    private final UUID CUSTOMER_ID = UUID.fromString("d215b5f8-0249-4dc5-89a3-51fd148cfb41");
    private final UUID PAYMENT_ID = UUID.randomUUID();
    private final BigDecimal PRICE = new BigDecimal("100");

    @Test
    void testDoublePayment() {
        /* Call process() twice with the same saga id. This will simulate that the first thread executes process() and the
        second thread only comes after first thread returns from this process() method. That is after the tx is committed.
        In this scenario we expect the second call to process() will return with that first if block in process().
        So the second call will get a log that the same outbox message is already processed!*/
        orderPaymentSaga.process(getPaymentResponse());
        orderPaymentSaga.process(getPaymentResponse());
    }

    /* Remember that we have optimistic locking control on PaymentOutboxEntity with the `@Version private int version` field.
     Also, we have unique index on payment_outbox and restaurant_approval_outbox tables. These two checks will be used in the process() ,

     In order to see the optimistic locking exception in test, remove those indexes by commenting them in init-schema.sql in order-container.
     Why? Because the db unique indexes will run before the optimistic locking check.

     After running, check the logs to see if optimistic locking exception occurred.

     We get ObjectOptimisticLockingFailureException for one of the threads. But the other thread does the job, because we get our outbox obj
     with the PROCESSING saga status in assertPaymentOutbox().*/
    @Test
    void testDoublePaymentWithThreads() throws InterruptedException {
        Thread thread1 = new Thread(() -> orderPaymentSaga.process(getPaymentResponse()));
        Thread thread2 = new Thread(() -> orderPaymentSaga.process(getPaymentResponse()));

        thread1.start();
        thread2.start();

        /* join() method will block the calling thread which in this case is the main thread that calls the method, until
        the thread whose join() method is called has completed. So by calling join() for both threads, we will be sure that both
        threads will be executed before the main thread exits this test method. This way we call process() from both
        threads almost at the same time.

        Note: We can use CountDownLatch instead of thread.join() .*/
        thread1.join();
        thread2.join();

        assertPaymentOutbox();
    }

    /* If you check the logs, you see this log: `OptimisticLockingFailureException occurred for thread<x>`. It doesn't matter as long as
    one of the threads does the job and the other one is rolled back which is the aim of using optimistic locking.*/
    @Test
    void testDoublePaymentWithLatch() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        Thread thread1 = new Thread(() -> {
           try {
               orderPaymentSaga.process(getPaymentResponse());
           } catch (OptimisticLockingFailureException e) {
               log.error("OptimisticLockingFailureException occurred for thread1");
           } finally {
               latch.countDown();
           }
        });

        Thread thread2 = new Thread(() -> {
            try {
                orderPaymentSaga.process(getPaymentResponse());
            } catch (OptimisticLockingFailureException e) {
                log.error("OptimisticLockingFailureException occurred for thread2");
            } finally {
                latch.countDown();
            }
        });

        thread1.start();
        thread2.start();

        /* Blocks until current count reaches to 0. */
        latch.await();

        assertPaymentOutbox();
    }

    private PaymentResponse getPaymentResponse() {
        return PaymentResponse.builder()
                .id(UUID.randomUUID().toString())
                .sagaId(SAGA_ID.toString())
                .paymentStatus(com.food.ordering.system.domain.valueobject.PaymentStatus.COMPLETED)
                .paymentId(PAYMENT_ID.toString())
                .orderId(ORDER_ID.toString())
                .customerId(CUSTOMER_ID.toString())
                .price(PRICE)
                .createdAt(Instant.now())
                .failureMessages(new ArrayList<>())
                .build();
    }

    private void assertPaymentOutbox() {
        Optional<PaymentOutboxEntity> paymentOutboxEntity =
                paymentOutboxJpaRepository.findByTypeAndSagaIdAndSagaStatusIn(ORDER_SAGA_NAME, SAGA_ID,
                        List.of(SagaStatus.PROCESSING));

        assertTrue(paymentOutboxEntity.isPresent());
    }
}
