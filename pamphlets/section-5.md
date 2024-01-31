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
- domain layer errors(exceptions). These errors are caught in <Entity>GlobalExceptionHandler classes that we create for each aggregate root(like order) or
maybe entity
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
- The order-dataaccess module implements the output ports of the order-domain which are the repository interfaces.

## 32-003 Data access module Adding Order JPA Entity

## 33-004 Data access module Adding JPA Repository & Adapter implementation

005 Data access module Adding Customer and Restaurant implementations

006 Messaging module Adding Mapper and Config Data

007 Messaging module Adding Publisher implementations

008 Messaging module Adding Listener implementations

009 Container module Adding spring boot starter class and config

010 Container module Explaining kafka consumer properties

011 Container module Adding Order database schema file

012 Creating Customer Service & Running Order Service