# Section 03 - Domain Driven Design (DDD)

## 8-001 domain-driven-design-SHARE
The main idea is to separate the business model from the infrastructure.

Both hex arch and DDD, put the domain logic in center of software and makes it independent of outside world.

Strategic DDD: Focuses on boundaries in a domain model and introduces the bounded context idea. Domain itself is the operational area of
a system. For example a food ordering app and it should have well-defined boundaries within that domain. 
Boundaries are defined using the bounded context.
![](img/8-1-1.png)

Strategic DDD also brings ubiquitous language.

Tactical DDD: Focuses on implementation details of the domain logic.

Entities: An entity must have a unique identifier that is assigned to it when it's created and it remains unchanged throughput the lifespan of the
entity. Two entities with the same identifier, considered to be the same object even if all other properties are different.
Likewise, two entities considered to be different, if their identities are different, even if all the other properties are the same.
![](img/8-1-2.png)

Entity objects are mutable objects as they will run business logic and update the properties based on the calculations.
However, it shouldn't mean you should create setter methods for every property of an entity. It should have state-changing methods with
well-defined names by using the correct verbs.

Aggregates: A group of entity objects that are logically related. For example an order process aggregate that includes order,
orderItem and product entities. An aggregate should be retrieved and stored as a whole in a consistent state. This requirement brings us
to the next concept which is aggregate roots.

Aggregates root: Responsible to keep the aggregates in a consistent state all the time. So an aggregate is owned by an AR and the identifier
of the root, also identifies the aggregate. One thing to help keeping the aggregate in a consistent state, is that aggregate can only be
referenced through the aggregate root from outside. So all the state altering ops should go through the aggregate root.
The entities other than aggregate roots, can not be referenced by outside objects. So when you completed all state change ops
for an aggregate, before saving the data, you should enforce strict validations each time to keep the aggregate in consistent state.

To decide which entity will be the aggregate root, you should consider if the entity will be modified independently and be accessed by the
identifier from the outside, say from other aggregates. In that case, that entity should be chosen as the root aggregate.

Value objects: Are used to bring context to the value. For example, to keep the price of an order, you may think having a big decimal field is
enough or to keep an identifier of an entity, you may think having a uuid field is enough. However, this big decimal or uuid field tells nothing
about the domain when you first look at them inside of an object. When we look at a domain object, it should be obvious to tell which
field is used for what purpose, so if you create a value object with the class named Money and keep an inner field big decimal, we can use
this value object for the price field and for uuid field, if we create an orderId value object and keep a uuid field inside, we can use it as
the identifier of the order domain obj. The benefits are:
1. you bring context to the value
2. you can have business ops in the value object with methods
3. you can validate the value of the object while creating the value object in the constructor

So value objs, bring context, allow business ops inside the obj & allow validations in constructor.

Since value objects are immutable, when you create one, you can't change it's value, if you wanna change the value, you should create a new value obj with
the updated value.

![](img/8-1-3.png)

Domain events

Eventual consistency: all reads of the system will eventually return the latest value, provided that no new updates are made.
When we run business logic on a domain through the aggregate root, after saving your changes, you can fire some event to notify the other contexts.
In the same way, if you want to run a business logic based on an advance on another domain, we can create domain event listeners and subscribe to
the events of that domain. The important thing while distributing and consuming events is to have a system with retry operation. For this,
a message queue or an event log will be a great match. In the implementation of domain event, we use kafka as the event log store and it'll also allow
to see the history of events for the new subscribers. **This is event sourcing where you keep the state of a system as an ordered log of events.**
However, note that we won't use the event source as the whole persistent model of the system. It will only be used to keep the
events produced by the domain event publishers and later these events will be consumed by the domain event listeners to run the required business logic.
![](img/8-1-4.png)

Now we go one level up in DDD.

Domain services: Coordinates business logic that spans multiple aggregates. Note that domain service is still in the core of the domain logic,
so it can not be reached from outside and to communicate with outside from the domain logic, we use application services.

app services: you should have an interface with methods that the outside objects require and expose that interface. Then you need to implement
this app service interface and accept the req.
![](img/8-1-5.png)

Regarding domain events, a domain event listener can be thought of as a special kind of application service. The only difference is that 
domain event listeners are not triggered by end user, but by domain events. So the domain event listener will be the first contact point when
the domain event is received and it will organize and call the related domain service for that event handling which internally will organize and
call the entities to run the business logic.

## 9-002 Designing Order Service domain logic components

## 10-003 Creating common domain module with base Entity and Aggregate Root classes
Delete the src folder in common module because this module will be used as a base module.

Note: Delete maven compiler properties as we have set them in the base pom.xml .

We override the equals and hashCode methods in the BaseEntity abstract class because each entity should have a unique id and equals and
hashCode methods are the contracts that are used to compare the java objects.

AggregateRoot is a marker class. It is still an entity that has a unique id but we created a new class and named it AggregateRoot to distinguish
the root object from the other entities.

## 11-004 Adding value objects and domain events to common domain module
We override `equals` and `hashCode` methods in BaseId abstract class as best practice.

We created OrderId class in valueobject package in the common-domain module, because we will need it from Order and OrderItem entities and
also from PaymentService and RestaurantService, so it is a common value object.

What other value objects should be common?

For example money, we will use it in the OrderService and PaymentService(to work on monetary calculations).

Value objects should be immutable, so we make it's fields immutable using `final`.

Remember the advantages of value objects:
1. brings context to the value. So when you look at a class, you understand what it holds as a value
2. when you have a separate class for value object like Money, you can have some business ops inside the value object. So we can delegate
some business logic to this value object

About Money value object:

For the BigDecimal ops like add and multiply, if the op includes monetary calculations, you need to be more cautious to care
the precision and rounding operations. For this, we add a method named setScale() in Money class.

BigDecimal(double val) constructor: Result is somewhat unpredictable because of the nature of fractional numbers.

We call the setScale() method before creating the money object with the BigDecimal value like in the add() method.

## 12-005 Implementing Order Service domain logic using DDD - Order Aggregate Root
If IntelliJ can't recognize the dep you just added to a module, click on maven reload.

In the entity package of order-domain-core, the Order class is an aggregate while OrderItem is an entity(so it extends the BaseEntity).

Note: We should have a unique identifier for entities.

We need to define getters for all fields(public ones OFC) of entities since they will be needed by the mappers.

Create a builder using `InnerBuilder` plugin so it's easier to create an object, instead of using a constructor with many params.
Use generate menu(ctrl + n) to create the builder with the final fields and choose `Generate builder methods for final fields` and
`Generate static newBuilder() method`. Note: It cannot use the generic type `<OrderItemId>` to set the base id, so we need to 
do it manually.

**With builder pattern, the constructor of the class should be private.** So we need to use the builder to create the object. We can't
use the constructor anymore because private constructors can't be reached outside of the class. 
Look at the constructor of `OrderItem` class.

Rename the created static method for creating a build from `newBuilder` to `Builder`.

Since that module is core domain module, we don't want to add any dep into it, to keep that module independent even from any framework
or library. So instead of adding lombok dep which has annotations to for example create the builder pattern, we decided to create
it using IntelliJ plugins. Also with this approach, you will see the constructor and builder code directly, it's not hidden
as in the lombok autogenerated code and if we want to do any validation during the object creations, we can do that there.

But in other modules like infrastructure modules, we use lombok to prevent boilerplate java code.

## 13-006 Adding Order Entity implementation methods
In the domain layer, in case the business logic fails, we throw a domain exception like `OrderDomainException`.
By creating a specific exception for each domain(like `OrderDomainException`), we will even add context to the exception, following
DDD. With this, we can look at the code or when we see the exception in the logs, we know which domain it comes from.

## 14-007 Adding state changing methods to Order Entity
![](img/14-1-1.png)

`Pay()`, `approve()`, `initCancel()` and `cancel()` are state changing methods of Order entity, to change the state of an order during
order processing. So basically these methods will help us to create a simple state machine to validate the previous state and apply
the next state(you can see the transition of state machine in the next diagram).
![](img/14-1-2.png)

The state machine: When an order is first initialized, it is in the Pending state. Then it will go to the payment service,
payment service can return two responses, payment successful or payment failed. If payment is successful, order should transit into
the Paid state. So in the pay() method, we should first check if order is in Pending state.

If payment svc returns successful and order status set as paid, this paid order is sent to restaurant approval. If approval is successful,
order should go into approved state.

If the restaurant approval fails, we know order is still in paid status, now order service needs to inform payment service to 
rollback the payment operation and we set the order status as cancelling and sent a cancel req to the payment service. We cover this scenario
while implementing SAGA pattern since this is an example of compensating transaction.

In cancel method of Order aggregate root, as we see in the 14-1-1 diagram, when this method is called, order can be in two states:
- pending: if order is sent to payment svc and payment failed, we should set the order status as cancelled. In that case,
the current order status will be **pending**. The cancelling state could also lead to cancelled state, which is in case of approval is failed,
order is set as cancelling and then the payment service is called for rollback operation. Then payment svc completes the rollback for
the payment, order svc should set the order status as cancelled. So the cancel() method has 2 possible pre-conditions for it's operation.

**We hold a history of failure messages in the Order entity for audit purposes and also to return to client when order status
is queried using the get endpoint.**

The Restaurant aggregate root will be used by the domain service directly, to run some business logic across the Restaurant aggregate
and the Order aggregate.

Before implementing the domain service, we should add the domain events.

## 15-008 Implementing Domain Events in Order Service domain layer
We will create 3 domain events:
- OrderCreatedEvent
- OrderPaidEvent
- OrderCancelledEvent

The DomainEvent is a marker interface to mark the class as a DomainEvent and also to mark the entity class using the generic type.

Note: We return domain events from the OrderDomainService. That means the event firing process will be on the caller service, which will be
the application service. This is an approach that we prefer for domain events handling process. So the events will still be created
in the domain core, either in entity or in domain service. However, **the event firing will be in the application service.** We do this because
before firing an event, the underlying business operation should be persisted into the DB. Because if we fire an event and persist op fails later,
we will end up with an incorrect event which would have never been fired.

Q: Why didn't we fire an event in the domain service or entity?

A: Because if I want to fire events in the domain core, we need to persist the business logic results in the domain service or entity,
since we want to persist operations before event firing. However, as we mentioned, we prefer to delegate the repository calls to the
application service, to prevent doing unnecessary job in the domain core module which should only focus on business logic. The domain has no
knowledge about event publishing or event tracking. **It only creates and return the events after running business logic.**

Application service will decide when and how to raise the events.

Q: Where to fire the event?

A: In application service. Domain layer should not know about how to fire the event.

You may have seen different approaches when it comes to the domain events handling process, such as firing the event in the domain entity or
in the domain service. However, I prefer to create and return the domain event from the domain core module and delegate the event firing process
to application service.

The second question: Where should we create the domain events? In entity or domain service?

A: Naturally, domain entities are responsible to create the related events, as they can be directly called from the application service.
Because in DDD, using a domain service is not mandatory. As mentioned, domain service is required if you have access to multiple aggregates
in business logic or you have some logic that doesn't fit into any entity class. However, here, **we also follow a personalized approach and
always integrate a domain service in front of the domain. So our application service will never talk to entities directly.** Because of that,
we can safely move the event creation into the domain service.

Q: Where to create the event?

A: Domain service or entities.

## 16-009 Implementing Order Domain Service