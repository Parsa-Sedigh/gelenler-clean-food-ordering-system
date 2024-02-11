# 05 - Completing Order service components

## 30-001 Application module Adding Web Controller
In the controller, we want to forward the client req to the domain layer and to communicate with domain layer, we have to use
**application service interface** which is the input port in hex arch terms.

Q: What would happen when there's an error in the client req or when there is a server error?

A: To handle that, we use a global exception handler using spring controller advice(`OrderGlobalExceptionHandler` class).

## 31-002 Application module Adding Controller Advice
**In java, method overloading is having multiple methods with same name but with different parameters.** Like the `handleException` methods
in `OrderGlobalExceptionHandler`.

**Note:** OrderDomainException is the exception thrown from the order domain layer.

Errors:
- domain layer errors(exceptions). These errors are caught in <aggregate >GlobalExceptionHandler classes that we create for 
each aggregate root(like order).
- data validation checks in domain services
- internal server errors during the req

For second and third type of errors, we want to create a generic exception handler. Because these types of errors can occur in the other
microservices like payment and restaurant service and propagated to the order service.
For this purpose, create a new module inside common module, named `common-application`. Remove the <properties> section in it's pom.xml ,
because the versions are specified in base pom.xml of the project.

Do not specify the local module versions(like common-application module) in pom.xml of modules, because the versions are specified in
one central place and it's in the base pom file of the project.

- `The order-application module` uses the input port of order domain which is `OrderApplicationService`(OrderApplicationService is injected
in OrderController). 
- The order-dataaccess module(it's adapter classes) implements the output ports of the order-domain which are the repository interfaces.

## 32-003 Data access module Adding Order JPA Entity
Data access adapter classes implement the output ports of the domain layer.

Note: The base path of all of our microservice packages is: com.food.ordering.system.<micro service name>.service .

For each type of aggregate in domain layer, create a package in dataaccess package of order-dataaccess module.
Then create 4 subpackages in each of those packages.

First create the JPA entities(in entity subpackage of dataaccess) to be used in repository.

Then create the repository interface using supreme JPA.

## 33-004 Data access module Adding JPA Repository & Adapter implementation
The mapper package in dataaccess module is to:
- create entity objects(dataaccess layer) from domain objects(domain layer)
- create domain objects from entity objects

Data access implementation for order entity is completed!

## 34-005 Data access module Adding Customer and Restaurant implementations

## 35-006 Messaging module Adding Mapper and Config Data
In the messaging module, use generic kafka modules to implement the adapters for the publisher output ports(that are 
interfaces) from the order domain layer. Also there, we create the kafka listener implementations to be used by the domain layer.

Create `OrderServiceConfigData` class in order-application-service submodule.

Note: We use 4 kafka topics to communicate between the microservices using domain events. 

## 36-007 Messaging module Adding Publisher implementations
Note: An example of an output port in hex arch is `OrderCreatedPaymentRequestMessagePublisher`.

We create separate publisher classes for each type of domain events. Although the publish() method implementations look similar in
these classes, it is wise to keep them separated in different publisher classes to easily maintain and change each 
publisher separately and for common operations like the kafka callback method, we still use a common helper class.

## 37-008 Messaging module Adding Listener implementations
`PaymentResponseMessageListener` is one of the input ports in the domain layer and it has the adapter implementation in the
order-application-service module. In PaymentResponseKafkaListener, we just need to listen to kafka messages and
trigger that adapter implementation.

The PaymentResponseMessageListener is in the input ports of order-application-service and it needs to be implemented in the
order-domain layer(the order-application-service submodule). Then it will be called from the PaymentResponseKafkaListener class.

The order-container will include the spring boot main class and the application configuration.

## 38-009 Container module Adding spring boot starter class and config

## 39-010 Container module Explaining kafka consumer properties
If we make the consumer group id, a random value, what would happen each time your restart your kafka consumer?

Since an offset is matched with the group id, it will not see any offset with new randomly created consumer group id,
so each time you will read the data from the start. This might be a use case if you really want to read the same data again and again,
but mostly it's not preferred.

## 40-011 Container module Adding Order database schema file
Note: When we implement the outbox pattern, we will create the required outbox tables in init-schema.sql as well.

In addition to the init-schema.sql for the order tables, we also need the restaurant schema, tables and the materialized view, just to 
test the order service.

Note: In business logic of order-service, there is a check in checkRestaurant method on the restaurant info which uses a materialized view.
Later, when implementing restaurant service, we will add restaurant schema info to the restaurant service itself to automatically create it(but
what is that `order_restaurant_m_view` in order service?)

For now, we run the schema sql code for restaurant's schema, table and the materialized view, ourselves. Again note that we will be creating the
init schema and data sql files for restaurant in the restaurant service later.

Build the code:
```shell
mvn clean install
```
The above code will also create the docker image for the order service using the spring-boot-maven-plugin with build-image goal that we have
in `order-container`.

Then we need to create the customer tables and then we can test the order service end-to-end.

## 51-012 Creating Customer Service & Running Order Service
For now, create a spring boot main class and create the DB objects for the customer service. Then we will use a materialized view to query
customer data from the order service. Later we will use CQRS pattern to use customer data in the order service.

```shell
# in infrastructure/docker-compose folder:
docker-compose -f common.yml -f zookeeper.yml up

# in another terminal(make sure zookeeper is running):
docker-compose -f common.yml -f kafka_cluster.yml up
```
Then open localhost:9000.

Then run the order-service(it's main class is in order-container). Also make sure postgres is running with correct username and password.

Run a POST req to: localhost:8181/orders with correct payload.

Then:
```shell
kafkacat -C -b localhost:19092 -t payment-request
```