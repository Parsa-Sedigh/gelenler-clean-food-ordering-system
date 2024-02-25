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

004 Application Service domain module Implementing input ports

005 Implementing Data Access module

006 Implementing Messaging module

007 Implementing  Container module
