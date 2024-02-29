# 08 - SAGA Architecture Pattern

## 70-001 Introduction to SAGA pattern
To complete a food order, the app will need to have a successful payment as well as a restaurant approval.
For this scenario, we use saga pattern using kafka as the event bus and let the services communicate using events with
choreography approach. We will still use the order service to coordinate the saga pattern. The order svc acts as a coordinator for
the saga flow because it initiates the saga by sending the OrderCreatedEvent to payment svc. At this point, the local DB of order svc
shows a pending order. Then, order svc gets the PaymentCompleted event from the payment svc and sets the order status as paid (in the
order svc local DB) and then asks for order approval to restaurant svc using the OrderPaid event. When order svc gets 
OrderApproved event, it finally completes the order by setting the status to approved in it's local DB.

In case of failure in any point, previous ops need to be compensated in saga(for this, we use rollback method in each saga step).

## 71-002 Implementing Order Payment Saga
Remove <properties> in pom.xml files. Only use it in the base pom.xml file of the whole project.

The idea is that if the next saga step fails, previous one should be able to rollback it's changes.

Why do we create this saga step in the order-application-service?

A: Because we use the order svc as the coordinator of saga flow and we want to keep all saga steps there in the order svc.
Create OrderPaymentSaga.

## 72-003 Using Order Payment Saga in Messaging & Implementing Order Approval Saga
The steps are:
1. order-payment saga(orderPaymentSaga)
2. order-restaurantApproval saga(com.food.ordering.system.order.service.domain.OrderApprovalSaga)

Why we use @Transactional in Saga classes like com.food.ordering.system.order.service.domain.OrderApprovalSaga?

Because we wanna commit the changes in the local DB before firing the domain event.

RestaurantApprovalResponseMessageListener is the interface that order messaging module uses after consuming 
restaurant-approval-response kafka topic.

```shell
mvn clean install
```

## 73-004 Testing the application end-to-end with SAGA pattern changes
Remove the old data of kafka and zookeeper by truncating the data in broker-1 ... broker-3(not the broker-x folder themselves) and
the content inside data and transactions folders of zookeeper.

Start zookeeper and then kafka using docker-compose and then init-kafka.yml.

Then check kafka manager web interface(you need to create a cluster manually because you truncated the data, name it food-ordering-system-cluster).

Then run the microservices(run the application service in container module of each microservice).

Note: For now, the customer svc will just create the customer DB objects and then exits.

Use kafka cat or kafka tool to see the data in partitions. The data is hex coded, you can use a hex decoder like online-toolz.com to
decode it. After decoding, you would see the serialized data in avro format, but the field values are still readable.

At the end, run:
```shell
docker-compose -f common.yml -f kafka_cluster.yml down
docker-compose -f common.yml -f zookeeper.yml down
```

## 74-005 Testing failure scenarios
Two failure scenarios.

Use an unavailable product id when sending create order req from client. The state of order after querying it, should be CANCELLED.
The scenario: After client sent a req, order will be created and then the payment will be done successfully, then in order approval
step, we will get an error from restaurant svc and the order svc will call the compensating transaction and tell payment svc to 
rollback the changes in payment svc local DB. During that rollback process, the order status will be in CANCELLING state. 
And in the end, if payment svc returns a PaymentCancelledEvent, the final status of order will be CANCELLED which is the status that is returned
from the GET endpoint of order svc.

The logs:
- order svc:
    - order is created
    - payment is completed in OrderPaymentSaga
    - OrderCreatedEvent is published into the topic payment_request
- payment svc:
    - is listening to payment_request. Payment svc processes the payment
    - returns a PaymentCompletedEvent and publishes a payment response avro model obj into kafka topic payment_response to notify whoever
    is listening to that topic which is order svc
- order svc:
    - creates an OrderPaidEvent and sends it to kafka topic restaurant_approval_request to notify whoever is listening to that
    topic(which is restaurant svc)
- restaurant svc:
    - the restaurant_approval_request topic is consumed by restaurant svc. It process the order approval event
    - in RestaurantDomainServiceImpl, order is rejected since a product of order is unavailable.
    - the rejected order status and failure messages are encapsulated in OrderRejectedEvent
    - RestaurantApprovalResponse avro model obj is sent to restaurant_approval_response topic
    - restaurant_approval_response topic is consumed by order svc. So OrderRejectedEvent notifies the order svc
- order svc:
    - processes the rejected order in RestaurantApprovalResponseKafkaListener.
    - in OrderApprovalSaga, we see cancelling order log
    - in OrderDomainServiceImpl we see `order payment is cancelling` log and cancels the order in local DB of order svc.
    At this point, order status will be in CANCELLING state.
    - An OrderCancelledEvent is created and published to topic payment_request(PaymentRequestAvroModel)
- payment svc:
    - is listening to payment_request topic and get the OrderCancelledEvent
    - now in PaymentRequestHelper of payment svc, we see the log: `Received payment rollback event for order id: {}`
    - in PaymentDomainServiceImpl we see: `Payment is cancelled for order id: {}`. In local DB of payment svc, the changes are rolled back.
    - a PaymentCancelledEvent is published using PaymentResponseAvroModel into payment_response topic. This kafka topic is listened by
    the order svc
- order svc:
    - we see: `Cancelling order with id: {}` log.
    - order status is set to CANCELLED
    - We see: `Order is rolled back for order id: {}`

Another scenario: Insufficient credit:
Create a new order with a high price for a customer with low credit.

- order svc:
    - Log: Order is created with id {}
    - Log: Received OrderCreatedEvent for order id: {} and this event is published by CreateOrderKafkaMessagePublisher
    into payment_request topic and payment svc will consume this event
- payment svc:
    - listens to payment_request and starts payment complete process
    - however, during domain logic checks(in PaymentDomainServiceImpl), it fails with insufficient credit.
    - PaymentFailedEvent is published into payment_response kafka topic which is listened by order svc. Order svc will also get the
    - failure messages
- Order svc:
  - Log: Processing unsuccessful payment for order id: {}
  - Log: order is rolled back for order id: {}
  - Log: OrderPaymentSaga: Cancelling order with id: {}
  - Log: OrderDomainServiceImpl: Order with id: {} is cancelled
  - order status is updated to cancelled in order svc local DB

![](img/section-8/74-5-1.png)

We're using saga and looks like we're handling the failure scenarios as well. However, this app might still fail. Because the
local DB transaction and event publishing op are not running in a single unit of work. Remember that we commit the DB changes to
be sure that the changes are there and then we fire the domain events. But what will happen if the publishing fais, or the consumers
of the events will fail before processing the required business logic?

To make this app more resilient, we will also apply outbox pattern next to saga pattern.