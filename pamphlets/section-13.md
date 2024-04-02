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