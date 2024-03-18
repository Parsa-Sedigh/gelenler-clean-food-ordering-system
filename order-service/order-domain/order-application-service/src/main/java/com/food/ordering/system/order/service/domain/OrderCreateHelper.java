package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.order.service.domain.dto.create.CreateOrderCommand;
import com.food.ordering.system.order.service.domain.entity.Customer;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.entity.Restaurant;
import com.food.ordering.system.order.service.domain.event.OrderCreatedEvent;
import com.food.ordering.system.order.service.domain.exception.OrderDomainException;
import com.food.ordering.system.order.service.domain.mapper.OrderDataMapper;
import com.food.ordering.system.order.service.domain.ports.output.repository.CustomerRepository;
import com.food.ordering.system.order.service.domain.ports.output.repository.OrderRepository;
import com.food.ordering.system.order.service.domain.ports.output.repository.RestaurantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class OrderCreateHelper {
    private final OrderDomainService orderDomainService;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderDataMapper orderDataMapper;
//    private final OrderCreatedPaymentRequestMessagePublisher orderCreatedPaymentRequestMessagePublisher;

    /* Inject the fields using constructor injection. */
    public OrderCreateHelper(OrderDomainService orderDomainService,
                             OrderRepository orderRepository,
                             CustomerRepository customerRepository,
                             RestaurantRepository restaurantRepository,
                             OrderDataMapper orderDataMapper
//                             OrderCreatedPaymentRequestMessagePublisher orderCreatedPaymentRequestMessagePublisher
    ) {
        this.orderDomainService = orderDomainService;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.restaurantRepository = restaurantRepository;
        this.orderDataMapper = orderDataMapper;
//        this.orderCreatedPaymentRequestMessagePublisher = orderCreatedPaymentRequestMessagePublisher;
    }

    /* You may remove @Transactional here, because we have @Transactional in the caller of this method which is
     OrderCreateCommandHandler.createOrder(). But you can also keep this, because the inner @Transactional will use the
     outer tx by default. Since the default propagation method is required in spring.

      So if there is a tx, it will use the existing tx, otherwise it will create a new one and because of this, keeping the
      @Transactional in the inner method is OK.

      Also, it's actually better to keep @Transactional here as well because in case this is called from another method that doesn't have
      @Transactional.*/
    @Transactional
    public OrderCreatedEvent persistOrder(CreateOrderCommand createOrderCommand) {
        // does customer of the order exist?
        checkCustomer(createOrderCommand.getCustomerId());
        Restaurant restaurant = checkRestaurant(createOrderCommand);

        Order order = orderDataMapper.createOrderCommandToOrder(createOrderCommand);

        /* validate and initiate order in domain core and then save it using repository.*/
        OrderCreatedEvent orderCreatedEvent = orderDomainService.validateAndInitiateOrder(order, restaurant
//                orderCreatedPaymentRequestMessagePublisher
        );

        saveOrder(order);

        log.info("Order is created with id: {}", orderCreatedEvent.getOrder().getId().getValue());

        return orderCreatedEvent;
    }

    /* Note: If we had to do more business checks(more complex business logic and checks), with the customer object,
    we would pass the customer object to the domain service and do the business logic checks there, instead of here in the
    application service. However, just to check the availability of a customer, we don't need to pass it to the domain service.*/
    private void checkCustomer(UUID customerId) {
        Optional<Customer> customer =  customerRepository.findCustomer(customerId);
        if (customer.isEmpty()) {
            log.warn("Could not find customer with customer id: {}", customer);

            throw new OrderDomainException("Could not find customer with customer id: " + customer);
        }
    }

    private Restaurant checkRestaurant(CreateOrderCommand createOrderCommand) {
        Restaurant restaurant = orderDataMapper.createOrderCommandToRestaurant(createOrderCommand);
        Optional<Restaurant> optionalRestaurant = restaurantRepository.findRestaurantInformation(restaurant);

        if (optionalRestaurant.isEmpty()) {
            log.warn("Could not find restaurant with restaurant id: {}", createOrderCommand.getRestaurantId());

            throw new OrderDomainException("Could not find restaurant with restaurant id: " + createOrderCommand.getRestaurantId());
        }

        return optionalRestaurant.get();
    }

    // gets the order and return the saved order object
    private Order saveOrder(Order order) {
        Order orderResult = orderRepository.save(order);
        if (orderResult == null) {
            log.error("Could not save order!");

            throw new OrderDomainException("Could not save order!");
        }

        log.info("Order is saved with id: {}", orderResult.getId());

        return orderResult;
    }
}
