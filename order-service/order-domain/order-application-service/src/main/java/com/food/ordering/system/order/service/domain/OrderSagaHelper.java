package com.food.ordering.system.order.service.domain;

import com.food.ordering.system.domain.valueobject.OrderId;
import com.food.ordering.system.domain.valueobject.OrderStatus;
import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.exception.OrderNotFoundException;
import com.food.ordering.system.order.service.domain.ports.output.repository.OrderRepository;
import com.food.ordering.system.saga.SagaStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class OrderSagaHelper {
    private final OrderRepository orderRepository;

    public OrderSagaHelper(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order findOrder(String orderId) {
        Optional<Order> orderResponse = orderRepository.findById(new OrderId(UUID.fromString(orderId)));

        if (orderResponse.isEmpty()) {
            log.error("Order with id: {} could not be found!", orderId);

            throw new OrderNotFoundException("Order with id " + orderId + " could not be found!");
        }

        return orderResponse.get();
    }

    public void saveOrder(Order order) {
        orderRepository.save(order);
    }

    SagaStatus orderStatusToSagaStatus(OrderStatus orderStatus) {
        switch (orderStatus) {
            /* If an order is paid and requires an approval(restaurant approval), it is in the middle of saga processing. */
            case PAID:
                return SagaStatus.PROCESSING;

            /* If an order is approved, that means saga is completed successfully. */
            case APPROVED:
                return SagaStatus.SUCCEEDED;

                /* A cancelling order status is set when order service publishes an OrderCancelled event to be processed by the
                payment service and rollback and compensate the payment changes for that order. */
            case CANCELLING:
                return SagaStatus.COMPENSATING;

                /* If an order is cancelled, it is already rolled back and compensated from the previous steps. */
            case CANCELLED:
                return SagaStatus.COMPENSATED;

                /* Matches with OrderStatus.Pending(pending order) when it is first created. */
            default:
                return SagaStatus.STARTED;
        }
    }
}
