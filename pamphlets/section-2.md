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
and security.

We will use DDD concepts and namings, when we implement the domain logic.