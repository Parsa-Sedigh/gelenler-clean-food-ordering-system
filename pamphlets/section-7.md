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

## 68-006 Implementing Messaging module

## 69-007 Implementing  Container module
