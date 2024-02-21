# 06 - Implementing Payment Service

## 52-001 Domain core module Adding Aggregate Root, Entity and Value objects
Note: We didn't extend CreditHistory and CreditEntry from AggregateRoot. Instead, we extended them from BaseEntity. Actually, you can
think this CreditHistory and CreditEntry as separate aggregate roots. For example, if you create a REST API to topup credits for a customer.
So they can act independently from a payment aggregate root. Therefore, they can be aggregate root themselves.

We won't implement this API to topup customer credit and instead initialize the data from an sql file. But we still we keep these entities
separate to emphasize that they don't necessarily be only included in a payment op.

## 53-002 Domain core module Adding Exception and Domain events
Create specific events but keep the common code in the base event class(`PaymentEvent`).

## 54-003 Domain core module Implementing Payment Domain Service

## 55-004 Application Service domain module Adding Mapper, Config and Ports
PaymentDataMapper will be our factory class that will create the domain objects from the input objects and create the output objects from
the domain objects.

Note: @AllArgsConstructor is required to use the @Builder.

**Note:** The ports package in <service>-application-service, only contains interfaces that need to be implemented with adapters.
Ports are only interfaces that need to be implemented with adapters.

We will implement the repository interfaces(output port) of output ports package in `dataaccess` module and we will implement the
message publisher(which is an output port) interfaces in `messaging` module.

But we implement the input ports in application-service itself(which is the module that the interfaces are defined). Because it will
be called with domain events from the other services like order-service.

## 56-005 Application Service domain module Implementing input ports - Part 1

## 57-006 Application Service domain module Implementing input ports - Part 2

## 58-007 Application Service domain module Refactoring fire event process
We need to have a reference to the publisher impl classes(like PaymentCompletedMessagePublisher) in the 
payment event classe(like PaymentCompletedEvent). However, the problem is that the publisher(publisher interfaces) is in the payment-application-service,
but the domain event class is in the payment-domain-core. **We can not put a dep from the domain-core to the application service.** Because
it's the core domain and we want it to be the most independent module. Also even if we decide to put that dep, it will create a cyclic dep.
Because payment-application-service already has a dep to the payment-domain-core.

The solution is: Since PaymentCompletedMessagePublisher extends DomainEventPublisher interface and DomainEventPublisher is in the `common-domain`
module, we can use DomainEventPublisher in the event classes directly. So in the PaymentCompletedEvent class we can add DomainEventPublisher field.

## 59-008 Implementing Data Access module
In the dataaccess module, we will implement the ouput interfaces from the payment domain (payment-application-service) with adapters.
We will have payment, credit entry and credit history repo adapters.

Add payment-application-service dep to pom file of payment-dataaccess and add payment-application-service dep to base pom.xml of the project and
in order to omit the versions in the pom of dataaccess module, add `<version>${project.version}</version>` in the base pom file. Now we can use
payment-application-service without specifying the version in other modules.

Note: In the repositoryImpl classes we should only return domain objects and not the JPA objects. Because the domain layer shouldn't know
anything about the JPAEntity(dataaccess layer).

In hex arch terms, an output port is an interface that requires an implementation which is an adapter.

## 60-009 Implementing Messaging module Adding Mapper and Publishers

## 61-010 Implementing Messaging module Adding Listeners
## 62-011 Implementing Container module