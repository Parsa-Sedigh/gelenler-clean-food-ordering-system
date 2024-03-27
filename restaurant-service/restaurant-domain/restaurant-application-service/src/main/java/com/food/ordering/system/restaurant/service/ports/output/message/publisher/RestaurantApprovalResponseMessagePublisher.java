package com.food.ordering.system.restaurant.service.ports.output.message.publisher;

import com.food.ordering.system.outbox.OutboxStatus;
import com.food.ordering.system.restaurant.service.outbox.model.OrderOutboxMessage;

import java.util.function.BiConsumer;

public interface RestaurantApprovalResponseMessagePublisher {

    void publish(OrderOutboxMessage orderOutboxMessage,
                 BiConsumer<OrderOutboxMessage, OutboxStatus> outboxCallback);
}