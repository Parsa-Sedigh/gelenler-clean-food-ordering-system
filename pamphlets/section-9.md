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


## 78-004 Refactoring Order domain layer Adding Outbox scheduler
## 79-005 Refactoring Order domain layer Adding Outbox cleaner scheduler for Payment
## 80-006 Refactoring Order domain layer Adding Outbox schedulers for Approval
## 81-007 Refactoring Order domain layer Updating OrderCreate Command Handler
## 82-008 Refactoring Order domain layer Updating Order Payment Saga - Part 1
## 83-009 Refactoring Order domain layer Updating Order Payment Saga - Part 2
## 84-010 Refactoring Order domain layer Updating Order Approval Saga
## 85-011 Updating the Order Application Service Test for Outbox pattern changes
## 86-012 Refactoring Order Data Access module for Outbox pattern
## 87-013 Refactoring Order Messaging module for Outbox pattern - Part 1
## 88-014 Refactoring Order Messaging module for Outbox pattern - Part 2
## 89-015 Testing Order Payment Saga