package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.order.service.domain.dto.create.CreateOrderCommand;
import com.food.ordering.system.order.service.domain.dto.create.CreateOrderResponse;
import com.food.ordering.system.order.service.domain.entity.Customer;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.entity.Restaurant;
import com.food.ordering.system.order.service.domain.event.OrderCreatedEvent;
import com.food.ordering.system.order.service.domain.exception.OrderDomainException;
import com.food.ordering.system.order.service.domain.mapper.OrderDataMapper;
//import com.food.ordering.system.order.service.domain.ports.output.message.publisher.payment.OrderCreatedPaymentRequestMessagePublisher;
import com.food.ordering.system.order.service.domain.outbox.scheduler.payment.PaymentOutboxHelper;
import com.food.ordering.system.order.service.domain.ports.output.repository.CustomerRepository;
import com.food.ordering.system.order.service.domain.ports.output.repository.OrderRepository;
import com.food.ordering.system.order.service.domain.ports.output.repository.RestaurantRepository;
import com.food.ordering.system.outbox.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class OrderCreateCommandHandler {
    private final OrderCreateHelper orderCreateHelper;
    private final OrderDataMapper orderDataMapper;
//    private final OrderCreatedPaymentRequestMessagePublisher orderCreatedPaymentRequestMessagePublisher;
    private final PaymentOutboxHelper paymentOutboxHelper;
    private final OrderSagaHelper orderSagaHelper;

    ////////////// If we used spring's event publishing capabilities //////////////
//    private final ApplicationDomainEventPublisher applicationDomainEventPublisher;

    public OrderCreateCommandHandler(OrderCreateHelper orderCreateHelper,
                                     OrderDataMapper orderDataMapper,
                                     PaymentOutboxHelper paymentOutboxHelper,
                                     OrderSagaHelper orderSagaHelper
//                                     OrderCreatedPaymentRequestMessagePublisher orderCreatedPaymentRequestMessagePublisher
//                                     ApplicationDomainEventPublisher applicationDomainEventPublisher
    ) {
        this.orderCreateHelper = orderCreateHelper;
        this.orderDataMapper = orderDataMapper;
//        this.orderCreatedPaymentRequestMessagePublisher = orderCreatedPaymentRequestMessagePublisher;
//        this.applicationDomainEventPublisher = applicationDomainEventPublisher;
        this.paymentOutboxHelper = paymentOutboxHelper;
        this.orderSagaHelper = orderSagaHelper;
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderCommand createOrderCommand) {
        OrderCreatedEvent orderCreatedEvent = orderCreateHelper.persistOrder(createOrderCommand);

        log.info("Order is created with id: {}", orderCreatedEvent.getOrder().getId().getValue());

//        orderCreatedPaymentRequestMessagePublisher.publish(orderCreatedEvent);

        ////////////// If we used spring's event publishing capabilities //////////////

       /* The listener of this applicationDomainEventPublisher, is the process() method in OrderCreatedEventApplicationListener and
        since we used @TransactionalEventListener on that process method, that listener method(process()) will only process
        when the createOrder() method is completed and the transaction is committed.*/
//      applicationDomainEventPublisher.publish(orderCreatedEvent);

        ///////////////////////////////

        CreateOrderResponse createOrderResponse = orderDataMapper.orderToCreateOrderResponse(orderCreatedEvent.getOrder(),
                "Order created successfully");

        paymentOutboxHelper.savePaymentOutboxMessage(
                orderDataMapper.orderCreatedEventToOrderPaymentEventPayload(orderCreatedEvent),
                orderCreatedEvent.getOrder().getOrderStatus(),
                orderSagaHelper.orderStatusToSagaStatus(orderCreatedEvent.getOrder().getOrderStatus()),
                OutboxStatus.STARTED,
                UUID.randomUUID()
        );

        log.info("Returning CreateOrderResponse with order id: {}", orderCreatedEvent.getOrder().getId());

        return createOrderResponse;
    }

    ////////////// If we used spring's event publishing capabilities //////////////

    /* Note: If we had to do more business checks(more complex business logic and checks), with the customer object,
    we would pass the customer object to the domain service and do the business logic checks there, instead of here in the
    application service. However, just to check the availability of a customer, we don't need to pass it to the domain service.*/
//    private void checkCustomer(UUID customerId) {
//        Optional<Customer> customer = customerRepository.findCustomer(customerId);
//        if (customer.isEmpty()) {
//            log.warn("Could not find customer with customer id: {}", customer);
//
//            throw new OrderDomainException("Could not find customer with customer id: " + customer);
//        }
//    }
//
//    private Restaurant checkRestaurant(CreateOrderCommand createOrderCommand) {
//        Restaurant restaurant = orderDataMapper.createOrderCommandToRestaurant(createOrderCommand);
//        Optional<Restaurant> optionalRestaurant = restaurantRepository.findRestaurantInformation(restaurant);
//
//        if (optionalRestaurant.isEmpty()) {
//            log.warn("Could not find restaurant with restaurant id: {}", createOrderCommand.getRestaurantId());
//
//            throw new OrderDomainException("Could not find restaurant with restaurant id: " + createOrderCommand.getRestaurantId());
//        }
//
//        return optionalRestaurant.get();
//    }
//
//    // gets the order and return the saved order object
//    private Order saveOrder(Order order) {
//        Order orderResult = orderRepository.save(order);
//        if (orderResult == null) {
//            log.error("Could not save order!");
//
//            throw new OrderDomainException("Could not save order!");
//        }
//
//        log.info("Order is saved with id: {}", orderResult.getId());
//
//        return orderResult;
//    }
}
