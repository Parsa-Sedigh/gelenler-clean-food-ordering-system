# Section 09 - Outbox Architecture Pattern

## 75-001 Introduction to Outbox pattern
Outbox pattern uses local ACID transactions to create consistent distributed translations.

In saga pattern, with a long running transaction, you will have a local data store transaction and also we'll have the events publishing and
consuming operations.

If you just first commit the DB transaction and publish, if the publish op fails, saga can not continue and you will leave the
system in inconsistent state. Instead, if you first publish the event and then try to commit db transaction, things can
go even worse! Because the local DB transaction can fail, in that case, you would have already published a wrong event which 
you should never have published. 

Until now, first we complete the local DB transaction and then publish the event. But this publish op can fail because of a problem in
service, in kafka, or in network communication. To resolve this issue, we need to combine saga with outbox pattern to obtain a consistent
solution.

We will be using pulling outbox table with a scheduler and keep the state of saga, outbox and order, in the outbox table for each
microservice.

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

## 81-007 Refactoring Order domain layer Updating OrderCreate Command Handler
Now in `OrderCreateCommandHandler`, we won't publish the event directly. Instead, we will persist the event in the local DB and use
scheduler to fire the event.

By default, spring uses spring proxy AOP and all AOP functionality provided by spring, including @Transactional, will only be taken into
account if the call goes through the proxy. That means the annotated method should be invoked from another bean. 
Also the annotated method should be public(in order to for example use @Transactional).

## 82-008 Refactoring Order domain layer Updating Order Payment Saga - Part 1
## 83-009 Refactoring Order domain layer Updating Order Payment Saga - Part 2
## 84-010 Refactoring Order domain layer Updating Order Approval Saga
## 85-011 Updating the Order Application Service Test for Outbox pattern changes
## 86-012 Refactoring Order Data Access module for Outbox pattern
## 87-013 Refactoring Order Messaging module for Outbox pattern - Part 1
## 88-014 Refactoring Order Messaging module for Outbox pattern - Part 2
## 89-015 Testing Order Payment Saga