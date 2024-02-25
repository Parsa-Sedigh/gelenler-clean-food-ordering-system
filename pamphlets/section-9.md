# Section 09 - Outbox Architecture Pattern

## 001 Introduction to Outbox pattern
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

## 002 Updating Order Service database schema and config for Outbox Pattern