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

010 Container module Explaining kafka consumer properties

011 Container module Adding Order database schema file

012 Creating Customer Service & Running Order Service