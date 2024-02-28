# 07 - Implementing Restaurant Service

## 63-001 Domain core module Adding Aggregate Root and Entities
The restaurant svc will approve an order after the payment is done. So it is the final step to complete an order.

If approval is completed in the restaurant svc, order svc will get an `OrderApproved` event and complete the order process.

Start with the implementation of domain-core module which is the most independent module.

The Product entity in restaurant-service is different than the one in the order service.

Changes required after using the inner builder plugin:
- rename newBuilder() to builder()
- move `public static Builder builder() {}` method in the body of the class instead of being in the body of the Builder
- change type and name of the ID field of Builder class to the generic type that the root class is extending

## 64-002 Domain core module Adding Exception, Domain events and Domain Service
We have created the `fire()` method of DomainEvent, to easily fire an event from the application service.

## 65-003 Application Service domain module Adding Mapper, DTO and Ports
**We do the validation and transaction management in the application service.**

In `@ConfigurationProperties(prefix = "restaurant-service")`, the prefix is the prefix we use in application.yml of the container module.

Data mapper classes are factory classes that will create the domain objects from the input objects that were sent by clients and also
create the output objects from the domain objects.

Note: The input ports will be implemented in the application-service(same module that the interfaces are defined) module and the
output ports will be implemented in the infra modules like dataaccess and messaging. 

## 66-004 Application Service domain module Implementing input ports

## 67-005 Implementing Data Access module
In restaurant dataaccess module, we want the restaurant entity, but we'd like to use the same entity class from the
order dataaccess module using the materialized view. So instead of repeating the code in the restaurant service and creating the
same entity, we create a common data access module. Then we can use it in all of the services.

Data access and messaging modules are the adapter implementations of output ports of application service module(domain layer).

## 68-006 Implementing Messaging module
For each event, we have created a publisher in publisher.kafka package of restaurant messaging module.
Create `OrderApprovedKafkaMessagePublisher` and `OrderRejectedKafkaMessagePublisher`.

The container module has all the deps to run the microservice including the domain module together with it's 
adapter implementations(dataaccess and messaging).

## 69-007 Implementing Container module

