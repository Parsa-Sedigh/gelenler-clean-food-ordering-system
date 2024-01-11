# Section 02 - Clean Architecture & Hexagonal Architecture

## 001 Introduction to Clean and Hexagonal Architectures
Put the domain layer in the center of software.

Dependency rule: Only allow pointing inwards towards the domain layer(center). For this, we can use dependency inversion principle 
and polymorphism to emerge any dependency relation.

Separate insides(domain layer that holds the business logic) and outsides(infrastructure layers).

The insides(domain layer) will be developed first and it will not depend on any other layer. So it should be the most independent and stable
layer as it is the heart of your software.

The outside will include all the dependent layers such as API that is called by end users, databases that is used to persist the results of
business logic, a message queue or event store to send notifications to other systems, an external service that needs to be called from the
business layer or even any framework that is used in the software such as spring. All of these outside parts are not actually your software.
They should be behaved as plugins to your software.

So the main principle of hexagonal arch is to isolate the domain from any of these dependencies(in other words, isolated businesses logic from
the infrastructure and data sources).

Adapters are the interchangeable components.

Primary adapters are the ones that use the core logic that is in domain layer. They use the input ports to call the implementations
that are defined in the domain layer.
In other words, primary adapters implement the input ports to execute use cases.

Secondary adapters are implemented in the external modules like DB or message queue. Secondary adapters implement the output ports and
called by business logic to complete external tasks.

In this arch, user input is processed by one or more primary adapters and passed to the core logic and the core logic interacts with the
secondary adapters only. Then output of the core logic will return to the end user using primary adapters again.

We use interfaces on input and output integration and DI(inject the implementation of ports at runtime) is used to pass the implementation of
the secondary adapters to the core logic and primary adapters to the outside such as API.

### advantages of clean and hexagonal arch
Clean arch enables us to leave as many options open as possible for as long as possible.

We always need to adapt for new tech changes to keep your software in a good shape, as mentioned, these changes has nothing to do with your
business logic. For example, today you may use a relational DB, tomorrow you may want to adopt for another relational DB or even to a noSQL DB.
In this case, by keeping your business logic separated, you will be able to easily change your data source.

In a traditional layered arch, say with 3 layers, user interface, business and data layer, we would have all of our deps point in one direction.
Each layer above depending on the layer below. So in that case, your business layer will depend on the data layer. This will
prevent changing the data layer without touching the business layer.

With clean and hexagonal arch, all deps will point to the business layer, making the business layer stable and independent of any other dep.

Ports and adapters help to reverse the deps and make the business logic independent. We use dependency inversion principle to make this happen.

With dependency inversion principle and polymorphism, any source code dependency can easily be inverted and the low level modules like the
data source layer will be plugins to high level modules like business logic.

With clean arch, you will be able to delay the implementation decisions of all deps and can just implement your business logic without
thinking any limitations of any data source or framework.

You can even test your business logic completely by simply mocking the deps.

You can replace any adapter implementation without touching the business layer. And you can maintain each layer separately without affecting
other layers.

It will also allow to separate development and deployment of different layers and modules.

### Cons
Writing more code. Sometimes you even need to duplicate some code like having different data transfer objects for different layers to represent some data.

Note: Your app should be independent of frameworks, user interface, DB and external services.

### Business logic in clean arch
#### Entities
In clean arch, the core business logic is implemented in the entity objects. Entities consist of the critical business rules of the system.
For example, prior to creating an order, what's the state of the order, checking that the total price is correct or checking if the order
is in stock, are the critical rules that need to run before placing an order.

#### use cases
one layer above the entities, we have use cases which handle the use cases of a system, by orchestrating the entity objects.
Use cases harmonizes the entities by calling them in the correct order and to achieve the critical business rules. Besides the critical rules,
there could be some application specific business rules such as having a discount on an item temporarily or checking if a user doesn't exceed it and
allow daily purchase limit. These type of rules should be implemented in the use cases.

Now what is the role of DDD in clean arch?

The high level approach is the same in clean arch and DDD. Both has a domain layer isolated in the core of the system and attaching
the lower level deps into it such as user interface, DB and external services. However, there's a slight difference in the domain core itself.
In clean arch, we have entities which are the core domain elements and keep the enterprise business rules. Now use cases keep the 
application business rules. Use cases are responsible to orchestrate and call the entities as per the use cases requirement.
In DDD, we still have the entity that has the core business rules, but this time it has an additional aggregate and aggregate root concepts.
An aggregate root is the main entity that orchestrate the core business rule of the current context. This context is called as bounded context.
An aggregate is a group of business objects which always needs to be in consistent state. Therefore, we should save and update
aggregates as a whole inside a transaction. An aggregate root is a class which works as an entry point to our aggregate and all business operations
should go through the root. This way, the aggregate root will make sure that the aggregate is in a consistent state.

Bounded context: Boundary within a domain with a particular domain model.

Aggregate: Group of objects that always needs to be in consistent state.

One layer above the entities, this time in the DDD, are domain services. This can be thought as the equivalent of use cases, if we compare with
clean arch, although conceptually they are not the same. In the domain service, you should have the business logic that spans multiple
aggregate roots, so that they can be implemented in a single bounded context. A domain service can also include a business logic that doesn't
fit into an entity naturally.

Domain service: business logics that spans multiple entities and the logic that doesn't fit to an entity

Additionally, there's application service concepts in DDD. Application service will expose the required business layer methods to the outside
using ports and these ports will be implemented in the domain layer itself.
In the application service, we can also have the data mappings, validations and transaction management. Basically the app service should be
responsible to make any data adapter calls and gather the data to pass into domain service and entities.
Remember, domain service and entities have the core business logic and they shouldn't deal with things like gathering data, mapping the data,
or validating the data.

So there's slight difference on the implementation of domain logic between clean arch and DDD.

Application service: first contact point from outside to domain layer. Application service can handle validations, data mappings, transaction management
and security. This is actually the `order-application` module. It should pass the request into the domain layer

We will use DDD concepts and namings, when we implement the domain logic.

## 002 Designing components for Order Service using Clean Architecture
We use hexagonal and clean arch concepts before implementing the service.

Let's start with traditional three-layer approach. First the API layer. In this layer, we'll have the REST controllers that will serve
to end user interface. Also there will be some REST client or UI.

In domain layer, we put the business logic of the app. In the layered approach, each layer will depend on the lower level. So for example
there's an arrow from API layer to domain layer.

Now put the data layer which has the implementation of data source operations.

Business layer depends on data layer. So if we want to change the data layer to use
another data source, it will impact the business layer, because business layer depends on the data layer. So any change on the data layer will
require to re-compile or re-deploy the business layer.

Now apply clean arch principles:

The first thing is to make the business layer independent and stable. For that, we need to remove the dep from business to data layer(it needs to get
reversed). **To do that, put a data layer interface in the domain layer** and remove the dep arrow which was from domain to data layer.
Data layer interface is the output port definition that needs to be implemented with an adapter. This interface will include all 
datasource related methods that domain layer requires to complete the business logic. So whenever a data layer operation is required in domain layer,
we will use this data layer interface in the code, so we don't need to have a dep from business layer to data layer.
Since this is an interface, it will require an implementation class, which is called **secondary adapter** in hexagonal arch.

The implementation of this adapter will be in the data layer(the thin green box in data layer).

Data layer needs to depend on the business layer to implement this interface. So there's an arrow from data to business layer which is the correct
thing to do in hexagonal arch.
So we have reverted the relation! Now data layer depends on the business layer. This is called dependency inversion principle.
With this change, we immediately got two advantages:
1. business layer can be developed and tested without requiring a real data layer implementation
2. when we implement the data layer, we can change the implementation without affecting the business layer as long as they obey the contracts
on the data layer interface, which is in business layer(it's the data layer interface but in business layer to reverse the dep).

How to use the data layer interface while coding in the domain layer?

We need to use DI which will complete the dep inversion principle and allow us to use an interface without requiring the implementation at
compile time. At runtime, we will run both business layer and data layer together and the data layer adapter will be used as the
implementation of the interface that is used in business layer.

How can I run both business layer and data layer together?

For this, we use another component and call it container(the box at the top). If we think it as spring boot app, this will be the component
that has the spring boot's main class. The container has a dep to business, data and API layers. So when we run the spring boot app,
it will have all components as JAR files, so that the data layer interface will find it's implementor(which is the data layer adapter) in the
data layer at runtime.

We use dep inversion and polymorphism and the apply DI to use an interface(like data layer interface at business layer) at compile time which
will have the implementation class(secondary adapter) at runtime.

Even though we use DI, we don't add spring framework dep and annotations into the business layer classes. So the core logic will also
be independent of any framework. We accomplish this using a bean registration class in the container module. Here, we have defined the
output ports and the implementer of that ports as secondary adapters. In a clean arch, we should also define the input ports
in the domain layer(the green box on left of domain layer), we name this input port as domain layer port. This port is also an interface when we
think it in java and it will require an implementer and it will be implemented in the domain layer itself. So we add a 
domain layer implementer in the domain layer(the "domain layer implementation" red text). Who will use this input port(green box on left
on domain)? The API layer will inject and use the input port using DI and at runtime, the method calls will be delegated to the implementation,
which is in the domain layer(domain layer implementation red text). In hexagonal arch terms, the API layer that uses the input port is a
primary adapter. It is an adapter because it can be easily changed without touching the domain layer(the layer that it depends on), thanks to the
usage of the input ports. Look at the primary adapter red text in api layer.
Remember that the data layer adapter in the data layer was a secondary adapter(green box in left of data layer) in clean arch terms.

Let's say you need to have an external service call in business layer. 

How can we integrate this external service into the app according to clean arch?

Again, we add the ports to the business layer, this time we call it as external service ports and then we implement it in the external service
with an adapter which is a secondary adapter in hexagonal arch terms(the green box on left of external service).

About messaging cmp: By applying the dep inversion principle, we add a messaging port interface in the business layer and implement it with a 
messaging adapter in the messaging cmp(it's a secondary adapter). Let's say this adapter will use kafka. However, when you wanna change the
adapter with any other technology, it will be a painless operation thanks to the clean arch. So in future, we can easily change the message class from
kafka to any other solution.

We can develop and test the business layer independently.

In pom.xml <packaging>, we can use two types of packaging:
- jar
- pom

The default one is jar and it will create a jar file from the module. For the main project, it doesn't make sense to use a jar packaging as
there is no source code, so we set it as pom and make it the parent pom.xml file for the child services and modules.

packaging: jar vs pom

pom packaging creates a container for submodules with jar packaging.

We set relativePath as empty. relativePath is used to search local filesystem for parents maven projects. But since we have an external
parent project which is spring boot, we don't need to search local filesystem The parent pom will be loaded from the spring boot lib.
So set the relativePath to empty for external parent projects. Use only for local parent projects.

So setting relativePath to empty is best practice if your parent is an external project like spring boot.

Make sure the correct JDK version that you installed is chosen in the `project structure` sdk field and you see the same version of jdk
when running `mvn --version` in Java version.

The module for the api layer is named `order-application`.

The idea is to develop domains separately and make other modules, plugins to the domain module.

We also need a module that will create the runnable JAR file for the microservice and have a dependency to all of to other modules.
We call this module the order-container(the grey one on top) as it will contain all other modules and also apart from creating a
runnable JAR file, we will also create a docker image from that runnable JAR and run it on a docker container. That's why we named this module
as order-container.

Remove these from all the pom files:
```xml
<properties>
    <maven.compiler.source></maven.compiler.source>
    <maven.compiler.target></maven.compiler.target>
</properties>
```
Because we already set the maven-compiler-plugin with the right jdk version in the base pom.xml at the root. So we don't need to repeat that property.

Under order-domain, we wanna create two more submodules to separate the core domain from the application services which are the services
that expose domain methods to the outside.

We put the core logic in the `order-domain-core` module. So it will include entities, value objects and domain services.

Remove the src folder from order-domain because it's a parent module and will not include src files directly.

## 003 food-ordering-system
file

## 004 Creating Order Service modules using Clean Architecture - PART 2
Let's set the deps between order service sub-modules.

Note: We shouldn't add any deps in the core domain module(any submodule in order-domain module). Because it should be the most independent and
stable cmp including the business logic. But we need to add an internal dep which is `order-domain-core`, in the order-application-service, to be
able to call the core logic from the application service. After doing that, do maven reload.

We want to manage all versions in base pom.xml . Why do we put the dep in `dependencyManagement` of base pom.xml? Because
when we put a dep in dependencyManagement, it will help to define the dep app right, with the specified version, without actually downloading it.
When a submodule of service requires a dep, it will add this dep into it's dependencies section, but the version won't be necessary as it's defined
in base maven pom.xml . Now click on maven reload again.

So it's good to define all the modules with ${project.version} in the dependencyManagement of base pom.xml . Because we need to include all these
local deps for each other. So put all of them in base pom.xml .

We don't put the order-container in dependencyManagement of base pom.xml because that module won't be used by other modules. It will
have a dep to all modules to get all deps and create a single runnable JAR file that will run the order application as a microservice and 
also create a docker image to use it later in cloud deployment.

For example, on dataaccess module, it's gonna have the adapters for the output ports of the domain layer. So it will implement the interfaces from
the domain layer and therefore it should have dep to order-application-service.

The order-messaging module should also have a dep to order-application-service because it should implement the messaging interfaces from the domain
layer.

Note: order-application module is the initial contact point from the clients. It passes the req to domain layer so it has a dep to
order-application-service.

Now run `mvn clean install` cmd to see there's no issue with deps.

To run single command from `depgraph-maven-plugin`, first install `graphviz`. The maven plugin will use `graphviz` plugin under the hood.
Then run this single command:
```shell
mvn com.github.ferstl:depgraph-maven-plugin:aggregate -DcreateImage=true -DreduceEdges=false -Dscope=compile "-Dincludes=com.food.ordering.system*:*"
```
Note: `com.food.ordering.system` is the groupId.

All modules have dep to order-application-service and order-application-service itself to order-domain-core. domain-core is the most independent cmp.