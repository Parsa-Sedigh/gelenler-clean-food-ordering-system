# Section 09 - Outbox Architecture Pattern

## 75-001 Introduction to Outbox pattern
Outbox pattern uses local ACID transactions to create consistent distributed translations.

In saga pattern, with a long running transaction, you will have a local data store transaction and also we'll have the events publishing and
consuming operations.

If you just first commit the DB transaction and publish, if the publish op fails, saga can not continue and you will leave the
system in inconsistent state. Instead, if you first publish the event and then try to commit db transaction, things can
go even worse! Because the local DB transaction can fail, in that case, you would have already published a wrong event which 
you should never have published. 

**Until now, first we complete the local DB transaction and then publish the event. But this publish op can fail because of a problem in
service, in kafka, or in network communication. To resolve this issue, we need to combine saga with outbox pattern to obtain a consistent
solution.**

We will be using pulling outbox table with a scheduler and keep the state of saga, outbox and order, in the outbox table for each
microservice. We persist the events in the outbox tables and use schedulers to read and publish the events(the rows of outbox tables).

In outbox pattern you don't publish events directly, instead you keep your events in the local DB table called outbox table.
This table belongs to the same DB that you use for local DB ops. So that you can use a single ACID transaction to complete your DB ops in
the service and the insertion into the outbox table. Therefore, we will be sure that the event is created atomically within 
the local DB using ACID transaction. After this, you will complete the outbox pattern by reading the data from the outbox table and
publishing the events. For this, there are two approaches:
- pulling the table data
- change data capture approach: which will listen to transaction logs of the outbox table.

In both cases, you should do the implementation to be sure that you don't miss the events in the outbox table and they are safely
published into the message bus. We will use pulling outbox table approach and handle possible failure scenarios later.

Using outbox table, we will keep track of saga and order status for each op. Also we use outbox table to ensure idempotency and 
to prevent data corruption in case of concurrent ops. We will apply optimistic locks and DB constraints using the outbox table.

Note: We should consider state changes for both happy flow and failure scenarios.

![](img/section-9/001-outbox.png)
![](img/section-9/001-2-outbox-payment-failure.png)

## 76-002 Updating Order Service database schema and config for Outbox Pattern
In order svc we have 3 events:
- OrderCreatedEvent
- OrderCancelledEvent
- OrderPaidEvent

The first two events are used to publish a message to the payment_request topic and they will trigger the payment svc(payment svc is listening
to that topic).

OrderPaidEvent is used to publish a message on restaurant_approval_request topic and it will trigger restaurant svc.

So in order svc, we have two types of events:

One is for payment svc and the other type is for restaurant svc.

With outbox pattern, we will persist the events to a DB table. But if we keep all these types of events in the same outbox table,
we will be putting unrelated events in the same table. So we use two outbox tables in the order svc: One is for payment svc and one is for
restaurant svc. So create these tables:
- payment_outbox
- restaurant_approval_outbox

We pull the outbox table in a 10s interval. On prod, you want to set this value to less than 10s depending on the processing time of
a saga op. Ideally, these the outbox-scheduler-fixed-rate shouldn't be more than 2s.

## 77-003 Refactoring Order domain layer Adding Outbox models & Updating ports
We will implement the scheduler package later, as it will require to use the port definitions. So let's first create the output ports in the
ports package.

Output ports are nothing but interfaces to be implemented in the infrastructure modules(dataacess and messaging) and the domain layer will
simply use the interfaces and inject them at runtime.

Create `PaymentOutboxRepository` interface in output port package.

Why we pass BiConsumer to publish() method in `PaymentRequestMessagePublisher`?

A: Because we want to update outbox status as completed or failed depending on the result of the publish method. That interface will be
implemented in the messaging module with an adapter that will use kafka producer and only in that adapter we will know that if 
kafka producer `send` method successfully sent the data or not.

Note: Kafka producer send() method is an async method and it uses a callback method to be called later. When we send the BiConsumer as a param
to the publish method, we will be able to call it in the kafka producer callback methods.

Kafka producer callback methods handle failure and success cases. We call accept() method of BiConsumer in those failure and success
methods of kafka producer and update the outbox status in the local DB.

Note: In the end, we won't need `OrderCreatedPaymentRequestMessagePublisher`, `OrderCancelledPaymentRequestMessagePublisher` and
`OrderPaidRestaurantRequestMessagePublisher` because we will use the outbox message publisher like `PaymentRequestMessagePublisher` and 
`RestaurantApprovalRequestMessagePublisher` for each type of event and separate them in the outbox message obj using the payload obj
which is the domain event, for example, it has the payment order status for payment outbox message and for approval outbox message,
it has restaurant order status to make this separation.

So we deleted those old event publishers that I mentioned(commented them out).

## 78-004 Refactoring Order domain layer Adding Outbox scheduler
We didn't create any bean beside configuration bean in config package of outbox submodule of infrastructure module, but you may 
create an object mapper bean if you need custom object mapper configuration. Remember that we use object mapper for json serialization
of the domain events.

With spring-boot-starter-json dep, a default object mapper bean is created automatically and using this default bean will be
enough for us. In case you need to add some specific properties of the object mapper bean, you can create it in SchedulerConfig class
and for example add FAIL_ON_UNKNOWN_PROPERTIES false which will prevent failing if it encounters unknown json properties during
deserialization. But we don't customize any beans there.

To do payment outbox repository ops, create a helper class named `PaymentOutboxHelper` instead of injecting the
repository directly in `PaymentOutboxScheduler`.

Note: Made the `SagaConstants` class final and added a private constructor, because we don't want anyone to create an instance of that
class which is unnecessary.

Use `@Transactional(readOnly = true)` because this method doesn't change the state and only gets data.

In the payment outbox table, we will have the domain events for two types of events:
- order created
- order cancelling

Order svc triggers the payment svc(actually it's not direct messaging, payment svc is listening to kafka topic) for these two types of events
and in the orderStatusToSagaStatus method of OrderSagaHelper, we set the saga status as STARTED for a newly created order which is in
PENDING state. And we set saga status to COMPENSATING for an order that is in the CANCELLING status. So when we pass STARTED and COMPENSATING
as saga status to getPaymentOutboxMessageByOutboxStatusAndSagaStatus(), we actually ask for order pending or order cancelling events which are
the two types of events that can be in the payment_outbox table.

Note: Default propagation methods for a transaction is required. So if there's a tx already open when the method with @Transactional is called,
then that tx will be used, otherwise a new tx will be opened.

## 79-005 Refactoring Order domain layer Adding Outbox cleaner scheduler for Payment
We need another scheduler beside OrderPaymentScheduler to clean the data from outbox table.

When the outbox status is updated as completed, we can actually delete that data from the outbox table. This will prevent the outbox table from
getting too large which could affect the app perf. So create `PaymentOutboxCleanerScheduler`.

Note: Before deleting the completed outbox events of order payment outbox table, you might also create an archive table and move the data
there. Then you can use that table along with the logs for analyzing the system.

## 80-006 Refactoring Order domain layer Adding Outbox schedulers for Approval
Note: It's a good idea to create a dashboard for failed outbox messages, possibly with alerting option and list and analyze them.
We won't delete them in the outbox cleaner classes. We only delete the COMPLETED outbox messages. Because FAILED ops are not in the final state.
It is a situation that needs to be resolved because OutboxStatus is only set to `FAILED` in case kafka producer `send` method is failed.
That could be a network issue or an issue on kafka cluster.

We won't publish the events directly anywhere in our code. Instead, only the scheduler publishes the code.

We won't fire events directly with outbox pattern. Instead, we use outbox table to hold the events and fire them later with a scheduler.

## 81-007 Refactoring Order domain layer Updating OrderCreate Command Handler
Now in `OrderCreateCommandHandler`, we won't publish the event directly. Instead, we will persist the event in the local DB and use
scheduler to fire the event.

By default, spring uses spring proxy AOP and all AOP functionality provided by spring, including @Transactional, will only be taken into
account if the call goes through the proxy. That means the annotated method should be invoked from another bean. 
Also the annotated method should be public(in order to for example use @Transactional).

## 82-008 Refactoring Order domain layer Updating Order Payment Saga - Part 1
Saga flow:
1. create a OrderPaymentOutboxMessage from OrderCreatedEvent in OrderCreateCommandHandler with STARTED outbox status
2. PaymentOutboxScheduler pulled this data from the DB table and published it to the kafka topic
3. payment svc reads the topic, processes the payment and then writes results to the payment response topic
4. order svc listens to payment response topic in PaymentResponseKafkaListener and OrderPaymentSaga.process() is called
5. update the order as paid in local DB and get OrderPaidEvent
6. update payment outbox record with the new order and saga status values
7. fire an event to trigger the restaurant approval phase

Note: Without outbox pattern, we were firing an event in the PaymentResponseMessageListener.paymentCompleted() after the
PaymentResponseMessageListenerImpl.process() completed the tx and returns. But now, with outbox, instead of firing the events directly,
we need to save it to local DB and to save the approval event, we use approval outbox table. To do this,
inject ApprovalOutboxHelper in OrderPaymentSaga.

## 83-009 Refactoring Order domain layer Updating Order Payment Saga - Part 2
How does optimistic locking work?

When you read data from DB, you'll get a version column with a value(initially it will be zero). Then after running some processing logic,
when you want to update the original data, it will check the version field from DB. If the version is different, that means some other
tx has updated this data. If the versions are the same, the original tx will update the data and increment the version by one.

During the update of `OrderPaymentOutboxMessage` using the save() method, the version is incremented and the changes will be committed before
returning from the method.

Now imagine two threads enter the process() method. Both read the same outbox message(STARTED) with the same version and both pass the first
if condition, because both has the outbox message with STARTED saga status. Then both come to the paymentOutboxHelper.save() .
With optimistic locking with a version field, when these two threads try to update the same row simultaneously, one of them will get the lock
and do the update and the other one will wait till the first one commits.

When the lock is released, second thread gets the lock, but the version is already incremented by thread 1 and for therefore it won't be
the same version for thread 2 that it got when it first fetched the data at the start of the process(). In that case, we will get an
`OptimisticLockException` and the operation of the second thread will be rolled back.

The downside of using optimistic locking is this rollback op. If you have too much collisions, there will be more rollbacks which is not good for
perf. But with less collisions, like in our case, we would expect only a small amount of collisions here, it will perform better than
pessimist lock which will lock the row with a select for update manner and it will even prevent reading the data from another tx.

For monetary ops, it's still better to use pessimistic locking or constraints but for most other ops, optimistic locking is fine.

Another scenario is first thread gets the outbox obj and save it, but the method is not completed yet. That means the data is not committed yet.
Then another thread tries to read the same outbox message(record) from DB. In this case, there will be different behaviors depending on the
isolation level of DB. If the DB runs with uncommitted read, then the second thread won't find the record because although the data is
uncommitted(by the first thread or tx), it is changed, there is no data with the STARTED status anymore. So the second thread will
return directly and won't execute the process() method.

But if the isolation level is read-committed which is the default isolation level in postgres, then the second thread will 
wait the first tx to be committed and only will get the data when it is updated and since the status will be updated,
this thread will also return an empty result(talking about process() method of OrderPaymentSaga). So the second thread will again return
directly.

## 84-010 Refactoring Order domain layer Updating Order Approval Saga
Let's say two threads try to commit the changes after reading the same data and the @Version version field of that data is 0.
Since the op is an UPDATE op, only one of them will get the write lock on DB obj and commit the changes which will increment the version
by one. Only then, the second thread will get the lock an then it will compare the version of the row which was read at the start of the
tx(rollback() method of OrderApprovalSaga) with the current version which is 1. Since the versions mismatch(because the original version is
0 and now is 1), the persistent provider will throw OptimisticLockingException and the changes of the second thread or tx will be roll backed.

There is also a unique index on type, saga_id, saga_status cols on payment_outbox_table as in the approval_outbox_table. So it won't be
possible to insert the same data into the payment_outbox_table even if there was no optimistic lock checks.

## 85-011 Updating the Order Application Service Test for Outbox pattern changes

## 86-012 Refactoring Order Data Access module for Outbox pattern

## 87-013 Refactoring Order Messaging module for Outbox pattern - Part 1
In order-messaging module> publisher.kafka package, we should only have 2 publishers and should delete the rest. Because with outbox pattern,
we only have two interfaces in output ports of domain layer, that should be implemented in messaging module. Those interfaces are:
PaymentRequestMessagePublisher and RestaurantApprovalRequestMessagePublisher.

We set sagaId as the key of kafka messages so the messages belong to the same sagaId will be in the same partition and they will be
ordered because **kafka guarantees ordering in a single partition**.

## 88-014 Refactoring Order Messaging module for Outbox pattern - Part 2
We prevent throwing an error caused by OptimisticLockingFailureException in PaymentResponseKafkaListener because the
optimistic locking exception is not an exception that is required to be retried. It's a result of duplicate message. So logging and
ignoring it is enough here. No-OP for optimistic locking.

## 89-015 Testing Order Payment Saga
**Let's write an integration test for OrderPaymentSaga to test the concurrent access and optimistic locking.** We do this in `order-container` module.
In this integration test, we will use sql files to initialize some data on postgres db.

When we delete from orders table(for example in OrderPaymentSagaTestCleanUp.sql), related order_items and order_address records because
of the `ON DELETE CASCADE`.

We can use countdown latch instead of thread.join() .

## 90-016 Updating Payment database schema, config and package structure for Outbox
Change init-schema.sql of payment svc.

We pull the outbox table for 10s intervals for payment schedulers: `outbox-scheduler-fixed-rate: 10000`.

We will update the outbox status of OrderOutboxMessage after getting a successful res from the message bus which is kafka.

**We won't use the old publisher interfaces like PaymentCompletedMessagePublisher anymore. Because we won't return the domain events
and publish them directly. Instead, we persist the events into the outbox table locally and publish them later by reading them
with schedulers.**

So we won't return domain events in for example PaymentRequestHelper methods anymore.

## 91-017 Refactoring Payment domain layer Adding Outbox schedulers

## 92-018 Refactoring Payment Data Access module for Outbox pattern

## 93-019 Refactoring Payment Messaging module for Outbox pattern

## 94-020 Refactoring Payment domain layer Updating Message listener implementation
### Failure scenario
If we have a completed outbox msg in payment outbox table for a saga id, this means that payment svc sent the msg to kafka using the 
payment_response kafka topic and an ack received. After that, the payment_response topic will be consumed by order svc to get the
payment status.

If order svc somehow failed to process this payment res, it might ask for a payment again for the same saga id. In that case,
if the payment svc sees an already completed outbox msg, it should not process the payment again. Instead, it will assume that
the published payment msg is not processed by the order svc, so it should send it again.

We can't rely on optimistic locking in PaymentRequestHelper of payment svc, since we don't perform an update for the outbox msg in
`persistPayment` and `persistCancelPayment`. Remember that Optimistic locking can be used for concurrent updates of an existing db record 
There, we only insert a new outbox msg, so we will only rely on a unique constraint violation to prevent two threads processing op and
inserting the same outbox msg at the same time. We have the unique index in `payment.order_outbox` table.

## 95-021 Testing Payment Request Message Listener for double payment

## 96-022 Refactoring Restaurant Service for Outbox pattern - Part 1
We had 2 publisher interfaces before outbox. Those were: OrderApprovedMessagePublisher and OrderRejectedMessagePublisher, but now we only
have RestaurantApprovalResponseMessagePublisher with the outbox pattern.

## 97-023 Refactoring Restaurant Service for Outbox pattern - Part 2

## 98-024 Testing the application end-to-end with Outbox pattern changes
## 99-025 Testing failure scenarios