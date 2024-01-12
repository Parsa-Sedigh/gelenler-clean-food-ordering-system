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