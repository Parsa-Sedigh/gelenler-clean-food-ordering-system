# Section 10 - CQRS Architecture Pattern

## 100-001 Introduction to CQRS pattern
CQRS relies on separating the write and read components in a system. **This will enable scaling both parts separately** and
also enable using the correct technology or data store for each part separately. This leads to an eventual consistency because
the read store will be updated asynchronously with a small delay.

Eventual consistency is a concept used in distributed systems and it's actually the natural result of having distributed services.
If you can accept this type of consistency, it will enable highly scalable systems.

Eventual consistency: any update made on a distributed system will eventually be reflected to all nodes.

UI calls both parts separately and only gets data from the read store.

The data is updated in the read store only after write store is persisted and as mentioned, this leads to an eventual consistent system.

With CQRS, after persisting the events in write DB, usually an event is created and sent to event-store. In our example, after saving
the customer obj in customer svc local DB, we will create and send the CustomerCreatedEvent into kafka to hold the event. Then we will
consume that event from order svc. For this, we will create our query store for the customer data specifically in the order svc.

Actually after putting your events in the event-store, you can replay that event many times and create multiple query stores.
For example, you can pull your events and create data on elastic search or create data on an analytics DB to run some calcs.

So once you create your events separately, you will have infinite chances to consume and replay the events based on your requirements.
This is the power of separating write and read parts and it can be achieved using CQRS pattern.

Event store: store data as immutable events

Replaying events in kafka: Create a new consumer group. It will read the events from start by default.

## 101-002 Creating Customer Kafka topic & Customer modules
Until now, we had a customer table in customer DB and we were using a materialized view(order_customer_m_view) to serve the
customer info. In order svc, we were using a DB call to that materialized view which is in customer svc DB. For this, we defined a 
customer schema defined in JPA definition in order svc.

Instead of doing this cross-DB call, we will make a call to a local customer table in order svc, but to do this, we need to keep
this customer table up-to-date when the customer table in the customer svc is updated.

To achieve this, we will use cqrs pattern and while updating customer info in the customer svc, we will publish an event in
kafka topic and then listen these events from order svc to insert the new customer info in the order's customer table.

To build the java class from avro files, run mvn install on kafka-model module:
```shell
mvn clean install
```

All the config that we write in the application.yml file of each service, can be managed and pulled from a config server. This is
called **externalized configuration pattern**.

Order of developing:
1. domain-core
2. application-service
3. dataaccess
4. messaging
5. application module for REST endpoints

## 102-003 Implementing Customer Service modules
Customer entity is actually the aggregate root.

## 103-004 Updating Order Service to use local database table with CQRS pattern
Create the customers table in order schema.

The input port of domain layer(defined in application-service) is implemented in itself.

a mebibyte and megabyte are often used synonymously. However mebibyte is introduced to give a more precise measurement unit and leave
the megabyte as a rough calculation using 1,000,000 bytes instead of having the exact value.

- Mebibyte(Mi): 2 to the power of 20 or 1,048,576 bytes.
- Megabyte(Mb): 10 to the power of 6 or 1,000,000 bytes

## 104-005 Running Order and Customer Services to test CQRS pattern
We need to create a spring bean for the CustomerDomainService. Because CustomerDomainServiceImpl
is not a spring bean as we don't have spring dep in the domain-core module. But we still wrote the code to inject CustomerDomainService
in CustomerCommandHandler. So we create bean config class in container module for this.

After publishing the event to even store, the event-store can be used to create any type of query store. In this case, a query store
is the customers table in order svc db. Another query store could be to push the events to elastic search and make it full text searchable.
Or you can use it for audit purposes especially for update ops and keep the history of customer update and inserts in the event store or
you may consume and put that data to your analytics engine to work on that data.