# 08 - SAGA Architecture Pattern

## 001 Introduction to SAGA pattern
To complete a food order, the app will need to have a successful payment as well as a restaurant approval.
For this scenario, we use saga pattern using kafka as the event bus and let the services communicate using events with
choreography approach. We will still use the order service to coordinate the saga pattern. The order svc acts as a coordinator for
the saga flow because it initiates the saga by sending the OrderCreatedEvent to payment svc. At this point, the local DB of order svc
shows a pending order. Then, order svc gets the PaymentCompleted event from the payment svc and sets the order status as paid (in the
order svc local DB) and then asks for order approval to restaurant svc using the OrderPaid event. When order svc gets 
OrderApproved event, it finally completes the order by setting the status to approved in it's local DB.

In case of failure in any point, previous ops need to be compensated in saga(for this, we use rollback method in each saga step).

## 002 Implementing Order Payment Saga
Remove <properties> in pom.xml files. Only use it in the base pom.xml file of the whole project.

The idea is that if the next saga step fails, previous one should be able to rollback it's changes.

Why do we create this saga step in the order-application-service?

A: Because we use the order svc as the coordinator of saga flow and we want to keep all saga steps there in the order svc.
Create OrderPaymentSaga.

## 003 Using Order Payment Saga in Messaging & Implementing Order Approval Saga
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

## 004 Testing the application end-to-end with SAGA pattern changes

## 005 Testing failure scenarios