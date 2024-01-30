# 04 - Apache Kafka

## 24-001 Introduction to Apache Kafka
We will use kafka in the implementation of saga, outbox and CQRS patterns and keep the domain events in kafka to trigger
domain event listeners.

Kafka brokers: are the servers that form a cluster and serve to producers and consumers, to insert and read data.
Having multiple brokers in a cluster is important for high availability because with multiple server, kafka can replicate the data
on different nodes and increase resiliency of the system.

Topics: is a logical data structure that consists of partitions and partitions are the smallest physical storage unit that
holds to data.

As mentioned, some data can be replicated on different partitions, on different brokers to increase resiliency, this is achieved using the
replication factor property. For example if kafka cluster has 3 brokers and replication factor is 3, then the same data will be
replicated on three different partitions, on three different nodes.

Producers write data at the end of a partition and consumers read the data from the start of a partition and keep an offset with an id
to continue from the offset, in case of restarts.

Consumers achieve to read the data from the offset using the consumer group concept. A consumer belongs to a consumer group and
a consumer group will not read the same data more than once. Instead, it will keep reading the new data, using the offset.

Kafka has two important features:
- resiliency: is achieved using a replication factor and duplicating the same data on different partitions on different broker nodes.
- easy scaling: you can have parallel consumer threads up to the partition number in a topic. That means if you create a new partition,
you can create a new consumer and consume the data with a new thread concurrently.

Data is inserted as append-only log into the partitions and once inserted, the data can't be changed or updated, so it is immutable.
Since it inserts data to the end of a partition(append-only), it is fast. There's no need to search the place to be inserted.

### Zookeeper and schema registry
There is KIP-500 mode that allows to use one of the kafka brokers as the orchestrator(instead of using zookeeper), but it's still
not prod-ready.

Schema registry: provides a mechanism to manage and share the schemas between the producers and consumers. In the first call,
producers send the schema to the schema registry and get an id. Then producer uses this schema to serialize the data and then it will
send this data with the id to kafka broker. Then a consumer will read the id and the serialized data and get the schema from the
schema registry by querying it with the id and it deserializes this data using this schema.

There is a local cache on both producers and consumers. So instead of querying the schema registry each time again and again, they use their
cache to obtain the schema.

Let's run a kafka cluster using docker.

## 25-002 Running Apache Kafka using Docker
Use docker compose and run zookeeper, kafka cluster and schema registry together. Later, we replace this with CP helm charts in k8s,
then we run everything in k8s.

Create infrastructure module. Then, remove <properties> in it's pom.xml and src folder, as we won't use this module to create java
source files.

We run init_kafka.yml docker-compose file once, just to create the required topics when we first run the kafka cluster.

Now run the docker-compose files:
```shell
cd infrastructure/docker-compose

# first run zookeeper since cluster requires a healthy zookeeper when persisted volumes are used.
docker-compose -f common.yml -f zookeeper.yml up

# to test the health of zookeeper, use ruok command that's enabled in the docker-compose file of zookeeper.yml and in it's KAFKA_OPTS env.
# open a new terminal and run the below command. It should return `imok` if zookeeper is healthy.
echo ruok | nc localhost 2181

# now you can run kafka cluster(in another terminal window):
docker-compose -f commony.yml -f kafka_cluster.yml up

# in another terminal to create the topics(if not exist):
docker-compose -f commony.yml -f init_kafka.yml up

```
Now open kafka manager UI in browser by going to localhost:9000. Then you see there's no cluster listed there, because this kafka manager
has no discovery, therefore, we need to add our cluster manually. So add a new cluster:
`
cluster name: food-ordering-system-cluster
cluster zookeeper hosts: zookeeper:2181
`

To test the persistence of topics data, shutdown zookeeper and kafka cluster and start them again:
```yaml
docker-compose -f common.yml -f zookeeper.yml down
docker-compose -f commony.yml -f kafka_cluster.yml down

docker-compose -f common.yml -f zookeeper.yml up
docker-compose -f commony.yml -f kafka_cluster.yml up
```

Note: When using volume mappings, you should first start zookeeper and then start kafka cluster. Because kafka cluster checks the health
of the zookeeper at startup and fails if it's unhealthy.

In k8s, we will replace docker-compose with CP helm charts to run kafka cluster.

Now write kafka generic modules.

## 26-003 Implementing Kafka config data generic module
In kafka module, delete the <properties> in pom.xml and the src folder.

Set the parent of submodules in kafka module, the kafka module.

Start with kafka-config-data module.

## 27-004 Implementing Kafka model generic module
We have created 4 kafka topics and accordingly, we create 4 schema files(avro files).

Run this command for kafka-model module to create the java avro models from avro schema files:
```shell
# inside kafka-model module:
mvn clean install
```

If you see errors in generated files, do mvn reload so that intellij will see the avro deps.

## 28-005 Implementing Kafka producer generic module

## 29-006 Implementing Kafka consumer generic module
We will use generic kafka modules(`kafka` module) in order-messaging module.