## 116-001 Introduction to Change Data Capture
CDC approach to use it in outbox pattern as an alternative to the previous pulling the outbox implementation.

CDC is an approach to get the DB events in near real time by using DB tx logs. So it uses a push method instead of waiting and
pulling in some interval. Thanks to the push method approach, it has lower overhead and is more close to real time.

It reads the DB changes from `transaction log` which is called write a head log in postgres and these logs are actually
data manipulations that are done by each tx that ran on db engine. Basically every insert, update and delete op is written into
tx logs in the filesystem for recovering the data back to a consistent state in event of some failure while it's 
writing information to disk.

Writing to tx logs is fast, because no ordering or db constraints are in place in this log as opposed to the real DB table files.

To implement CDC, we use debezium which provides kafka connectors to write the data into kafka and also provides source connectors
for various DBs such as postgres.

Q: How CDC with debezium Works?

A: We know each insert, update and delete on a DB table are inserted into an append-only log on disk on all DBs. Debezium listens
these row-level changes and captures changed data and then sends this data to kafka using kafka connector. Debezium listens the tx logs
which is on disk and acts when there is a new data on the tx logs file. This way, it creates realtime streaming solution with
a push approach.

So instead of pulling the table and consuming cpu(our previous approach), we use push approach. With push approach, we can have
near real time solution in oppose to pull based approach which is not real time.

So we will be replacing the schedulers written in java using spring boot `@Scheduled` and use debezium kafka connector with
postgres source connector.

## 117-002 Debezium Kafka Connector
Look at the `debezium-cdc` branch.

We need to download the required schema-registry JAR files from confluent maven repo at:
`https://packages.confluent.io/maven/io/confluent/kafka-connect-avro-converter/7.2.5`.

Apart from the downloaded jar files, we have to add guava(from central maven repo), kafka-avro-serializer(from central maven repo) and 
kafka-schema-converter(from confluent maven repo) jar files to run debezium connect 2.2 v.

List of required files in the correct path:
![](img/section-13/117-1.png)

By default, debezium connector uses json serialization but we want avro format.

In the end, schema will have two objs:
- before: represents data before doing a change, so this will be empty if we had an INSERT.
- after

There is an SMT option which will transform the json obj into a simplified version so that instead of having before and after objs,
we only have the desired obj.

We have debezium connector container running on 8083 port, so query the connectors: `GET localhost:8083/connectors`.
We will get 200.

We have 4 outbox tables in system: 2 in order, 1 in payment and 1 in restaurant.

Now we need ot create debezium postgres source connectors.

## 118-003 Configuring Postgresql for Change Data Capture
Create debezium postgres source connectors. For this, we use connectors API. But before doing this, we need to change postgres config to
use it in CDC.

To use postgres in CDC, we need to enable logical replication. This means we need to replicate the data written in the tx log of postgres.
The tx log of postgres is called write a head log. So we need to replicate the content of the write a head log and while doing that,
we need to decode the contents that will be understood by the client of this replication streaming which is debezium in this case.

Find postgres config file by running:
```sql
show config_file;
```

```shell
sudo vim <config file path>;
```

Add these at the end of config file:
```
#shared_preload_libraries = 'wal2json'
# REPLICATION
wal_level = logical # minimal, archive, hot_standby, or logical(change requires restart)
max_wal_senders = 4 # max number of walsender processes (change requires restart)
#wal_keep_segments = 4 # in logfile segments, 16MB each; 0 disables
#wal_sender_timeout = 60s # in milliseconds; 0 disables
max_replication_slots = 4 # max number of replication slots(change requires restart)
```

Note: The wal2json plugin is not required anymore starting from debezium 0.10 and we will use pg output plugin by specifying it in
the connect api request.

We enable logical replication to copy the content of tx log to another location by decoding it to be used in CDC with debezium.

max_wal_senders = 4 : so there will be 4 different processes running in parallel to send replicated data to the target location.

We used 4 because we would have 4 outbox tables to stream their write ahead log. We would also have 4 debezium connectors.

For most apps, a separate replication slot will be required for each consumer.

Relation between write ahead log, output plugin, replication slot and consumer:

When the data is changed in a row in a postgres table, that change is recorded in the write ahead log. Then if
logical decoding is enabled, for each data manipulation such as insert, update or delete, the logical decoding translates the
data manipulations using an output plugin. We will use the default pg-output plugin. The output plugin changes the records from the
write ahead log format to the plugins format which is json in the pg-output plugin. Then the decoded is written into replication slots
and from there, the consumer can read this data to be used in the downstream component.

The consumer is debezium source connector in this case.

The replication and logical decoding in postgres, provides an efficient way to stay up to date with data changes in your postgres DB.
We write once to the reliable postgres tx log, then use that data in multiple consumers for different purposes such as with 
debezium source connector to use with outbox pattern. Here instead of a pull model where each component queries postgres at some
interval, we use a push model, where postgres notifies the other apps for each change.

In case of postgres restart, a slot may resend the changes, so it works with **at least one** delivery semantic and 
the consumer needs to handle this situation which is handled in debezium connector.

However, after getting the data from slots, the debezium client will use a kafka producer to send the data to the target topic and
in case there is a network issue in the acknowledgement at that point, the data can still be sent twice or more to the kafka topic,
so it's still working with at least one delivery semantic with debezium connector. So we need to have **de-duplication** of the same messages
in kafka consumers, as we did similarly in pulling outbox table approach.

We can query the replication slots:
```sql
select * from pg_catalog.pg_replication_slots;
```

To delete a replication slot:
```sql
select pg_drop_replication_slot('<slot name like order_payment_outbox>');
```

We may need to monitor the slots and detect the unconsumed ones because if a slot stream isn't being consumed, postgres will keep
write ahead log files for those unconsumed changes which can cause storage full.

We also need to update max_connections to 200. By default it's 100. However, when we use multiple instances for the micro-services,
Spring uses a connection pool and it assigns maximum 10 connections by default. So if we had 10 services, postgres max conn will be reached.
Plus we need to connect to postgres through pgadmin and there are connections from debezium as well, so make it 200.
Otherwise, we will get too many clients already error from postgres.

To see this value:
```sql
show max_connections;

-- to see active connections:
select * from pg_stat_activity;
```

Now look for pg hba config file:
```sql
show hba_file;
```

Then in that file, add these at the end:
`
#### REPLICATION ####
local replication postgres             trust
host replication postgres 127.0.0.1/32 trust
host replication postgres ::1/128      trust
`
These lines configure the client authentication for db replication.

Restart postgres sql:
```shell
sudo -u postgres /Library/PostgreSQL/15/bin/pg_ctl -D /Library/PostgreSQL/15/data restart
```

## 119-004 Debezium source connector for Postgresql
To create debezium postgres connector:
`POST localhost:<debezium connector port like 8083>/connectors` and the json payload is in debezium-connectors.json .

In the payload: 
- tasks.max: 1 - this can not be more than 1 with any debezium DB source connector. This is natural. Because the data changes of
postgres are written sequentially to write ahead log and replicated to a slot and then it's read by the consumer again sequentially.
So with a single connector, we can't have enough parallelization for better perf. For concurrent processing, we need to create
multiple connectors that read from different slots for different tables.

Note: if you deploy multiple instances of debezium postgres connector, make sure to use distinct replication slot names.
If you need more connectors(which should have distinct slots), we need to increase the max_replication_slots of postgres config and
restart it.

The default replication plugin of postgres is pgoutput(after postgres v10). The pugoutput plugin doesn't need any library to be installed.
But other plugins like wal2json, we had to install the plugin and set it in the postgres config file.

To query schema registry to get the avro schemas:
`
GET localhost:8081/subjects/debezium.order.payment_outbox-value/versions/latest/schema
GET localhost:8081/subjects/debezium.order.restaurant_approval_outbox-value/versions/latest/schema
GET localhost:8081/subjects/debezium.payment.order_outbox-value/versions/latest/schema
GET localhost:8081/subjects/debezium.restaurant.order_outbox-value/versions/latest/schema
`
then get the response and save it to an avro file in kafka-model module. To do this for all of the connectors.
We called these files `debezium. ...`.

We wanna create the topics manually instead of auto-creating them. Update init-kafka.yml .
We also create the avro schemas manually using schemas in avsc files. This would be better for a prod app because auto-creation of
topics and schemas will prevent having full control over the app. However, for now, we keep the auto-generation of avro schemas of
debezium connectors.

```shell
mvn clean install
```

About generated debezium files: For each outbox table schema, we have a separate envelop and value classes in different packages
But the block and source classes are common so they are shared.

Everytime we need to run zookeeper and kafka-cluster, then run init-kafka compose file to create the topics, then create 
4 debezium connectors. For this, we create a shell script and we put some checks between the steps.

## 120-005 Creating the startup and shutdown scripts

## 121-006 Implementation changes for Change Data Capture - Part 1
We don't need the scheduler implementations that are pulling the outbox tables and processing the data. You can delete them.
We can keep the cleaner schedulers that can be used to clean up the outbox tables.

Also delete the kafka message publishers that are called from schedulers. Like PaymentRequestMessagePublisher and
RestaurantApprovalMessagePublisher . We're now using debezium for publishing.

We deleted the schedulers because producing outbox messages will be handled by the debezium connectors.

When starting from scratch using CDC, it's good to use a table that debezium suggests as mentioned in debezium outbox event router guide.
With this table structure, we can also use a transformer class which will extract the required fields instead of getting before and
after objects. In that case, we don't do the state updates on the outbox tables and we can actually insert and immediately delete the
outbox record because after the insertion, it'll be already be available in the DB tx logs. In that case, we would have to eliminate the
delete ops just as we did for update op, so accepting if op == "c".

## 122-007 Implementation changes for Change Data Capture - Part 2

## 123-008 Implementation changes for Change Data Capture - Part 3
One customer id can create hundreds of orders simultaneously. For this, we can create a load test using a single customer id and
create up to 3000 order reqs that is consumed by 3 different instances for each order, payment and restaurant services to take advantage of
3 partitions of kafka topics.

The partition number is the natural way of creating a parallelism in kafka processing. So with 3 partitions, we can go up to
3 consumers that will run concurrently.

Now in the scenario where we use a single customer id for a lot of reqs coming at the same time, there's a high chance of getting
concurrent update issues. So if we have a list of messages and process the first N number of items, but get an error in the N + 1 item,
we will need to re-process the first N items which will create a unique constraint exception which will be eliminated in 
PaymentRequestKafkaListener > receive() > catch(DataAccessException e) . However, since we expect this occur too much,
that will decrease the overall perf, so instead of using a List of messages in PaymentRequestKafkaListener, we prefer to use a
single item. So why do we expect that much issue there?

In PaymentRequestHelper.persistPayment we have a check for credit entry and credit history.
Imagine multiple reqs(threads) arrive with same customerId. After fetching a credit entry in a thread, before fetching the history,
another thread might already updated the history. So in that case, we will get inconsistent credit entry and history which will
cause the business validation fail(in validateCreditHistory). To solve this issue we have 2 options:
- applying pessimistic locking
- applying optimistic locking

We will apply both in different branches and compare the perf test result.

Usually in case of too many collisions which requires to rollback previous ops, optimistic locking will be slower. So we prefer
pessimistic locking in these situations. However, pessimistic locking will serialize all the incoming reqs in that part of code,
so we won't have any concurrent processing at that point in payment svc. Pessimistic locking is easier to implement, so let's
start with that.

If we use pessimistic lock on getCreditEntry() in persistPayment(), it will make other concurrent threads to wait in their
getCreditEntry() calls until the lock is released and the lock is released only when the current tx is committed or rolled back.
Remember that the tx is committed after returning from @Transactional method, so the next thread that gets the lock, will see the
latest customer credit entry. Since getCreditHistory is called after getCreditEntry, there's no chance of any other thread changing
the history of data after getting the credit entry because now all the threads will be blocked until the lock is released by the
current running thread.

Now because of this pessimistic lock, a thread might wait for some time and may also get a timeout exception.

Or when we implement optimistic locking, it can get an optimistic lock exception multiple times which may cause 
the offset commit timeout to be expired, so that a single msg can arrive multiple times from kafka into this listener. So
we reduce the List<PaymentRequestAvroModel> to a single msg, to prevent re-processing the previous items.

We need to set batch listener config of kafka for payment to false.

You can double-check the debezium topic names by looking at the package structure in avro schema files of kafka-model.

```shell
# in docker-compose folder
chmod +x start-up.sh
chmod +x shutdown.sh

./start-up.sh

# check if containers are running:
docker ps

# then run services
```

Note: It's good to insert and immediately delete the outbox record and just use the tx log to get the outbox records through debezium.
In that case, for de-duplication of msgs, we would need another table such as a message_log table that would have a unique key
with the id of each msg. Remember we do the de-duplication using the data in the outbox table, since we don't delete them immediately and
we can still use the cleaner scheduler to clean the completed msgs. This prevents the outbox table gets large.

## 124-009 Running multiple instances of services  with a Jmeter performance scenario
In intellij, go to edit configuration, create 3 applications for order, payment and restaurant svcs and for the first order svc,
click on `modify options` and choose: `Add VM options` and set these for apps:
- order svc 1: -Dserver.port=8181 -Dkafka-consumer-config.concurrency-level=1
- order svc 2: -Dserver.port=8182 -Dkafka-consumer-config.concurrency-level=1 -Dspring.sql.init.mode=never
- order svc 2: -Dserver.port=8183 -Dkafka-consumer-config.concurrency-level=1 -Dspring.sql.init.mode=never
- payment svc 1: -Dkafka-consumer-config.concurrency-level=1 
- payment svc 2: -Dkafka-consumer-config.concurrency-level=1 -Dspring.sql.init.mode=never 
- payment svc 3: -Dkafka-consumer-config.concurrency-level=1 -Dspring.sql.init.mode=never
- restaurant svc 1: -Dkafka-consumer-config.concurrency-level=1
- restaurant svc 2: -Dkafka-consumer-config.concurrency-level=1 -Dspring.sql.init.mode=never
- restaurant svc 3: -Dkafka-consumer-config.concurrency-level=1 -Dspring.sql.init.mode=never

`-Dspring.sql.init.mode=never`: we only want to do the sql initialization in a single instance, otherwise all three instances will
start creating the schema at the same time and it will create race condition issue as all will be working at the same time to execute
that sql.

Note: Since we don't have a REST api in payment svc, we don't expose any ports.

The concurrency-level defines the number of threads that will be created in the spring @KafkaListener. Since we have 3 partitions
on kafka topics, on the consumer side we can create 3 threads to consume each partition in parallel. But since we create 3 instances
of each svc, instead of having 3 threads in an instance of a svc, we will create one thread per instance so that in total, we will have
3 consumer threads running in different service instances. Since all instances of a svc share the same consumer group,
in the end, 3 partitions will be distributed to the instances of the svc so that each instance will 
consume a single partition of a kafka topic.

Then increase max heap of intellij to be able to run all these services in change memory settings. Use 4096 MiB.

Then run startup shell script.

Make user's credit to 50000 with 70000 credit and 20000 debit and then make a product's price to 1 so that we can send 50,000 order
for this product using the customer with this credit.

```shell
brew install jmeter

jmeter
```
Then create a test plan with name: `food-ordering-system-load-test`.

Then create a thread group. For now we choose 1 thread which means 1 user.

Ramp-up period: Is the time elapses between the threads to run. So if we set thread count to 10 and ramp-up to 1, each thread will
start one after another waiting 1 second in between.

Loop count: the number to repeat the same test.

Then for the same thread group:
- add > config element > http request default.
- add > config element > http header manager . Set Content-type: application/json.
- add > sampler > http request and name it: Create Order 1 and set the port to 8181 and left the protocol and server name as empty since
they will be coming from the http request default. Set path as `orders`.

Under http request, from post processor, create json extractor. Choose main sample and sub-samples. 
In names of converted variables: `orderTrackingId_1`. In json path expression: `$.orderTrackingId`.
Then copy the req and name it with 2 and set the port to 8182 and order `orderTrackingId_2` and similarly for third req.

Then create while controller. Uner this, create new http req:
- name: Get Order Result
- path: orders/${orderTrackingId_1}
- then for this req, create json extractor:
  - names of converted variables: orderStatus_1
  - json path expressions: $_orderStatus

Then in while controller, check if this extracted orderStatus val is not approved and not cancelled which means the while loop
will continue until the order svc GET API returns approved or cancelled status which are the final status values for our process(indicate
that the process is finished).
Any other values like PENDING, PAID or CANCELLING are intermediate status values.
`${__javascript("${orderStatus_1}"!="APPROVED" && "${orderStatus_1}"!="CANCELLED")}}`

Copy the while controller for ports 8082 and 8083 and use `orderTrackingId_2<3>` and `orderStatus_2<3>` in json extractor.

To see the results, add: Listener > Graph results and Listener > View results tree

Now run the test. Then run it with 300 threads which means 300 orders approved.

Then write a text file to write the performance test results.

In `300 records(orders) -> 10 seconds`, we got the 10 seconds from top right corner of jmeter which means how much it took 
the load test to finish.

Now test with 500 threads and we will get 1500 orders.

Then run with 1000 threads. It took 49 seconds.

To show the requirement of locking credit entry, remove pessimistic locking in CreditEntryJpaRepository interface and
rerun all services again. Now if we send 10 threads(30 in total) and check the orders table, we get 27 CANCELLED because
credit entry is not consistent with history. This happens because of the concurrent create order reqs coming for the same customer.
So we need locking there, but we can also use optimistic locking as an alternative to pessimistic locking in that CreditEntryJpaRepository as well. 

## 125-010 Using Optimistic locking & Comparing Lock strategies with Jmeter load test
## 126-011 Comparing Change Data Capture & Pulling the Outbox table using Jmeter load test