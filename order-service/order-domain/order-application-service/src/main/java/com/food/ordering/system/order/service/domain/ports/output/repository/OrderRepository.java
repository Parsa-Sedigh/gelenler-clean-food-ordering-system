package com.food.ordering.system.order.service.domain.ports.output.repository;

import com.food.ordering.system.order.service.domain.entity.Order;
import com.food.ordering.system.order.service.domain.valueobject.TrackingId;

import java.util.Optional;

public interface OrderRepository {
    /* As you see, we pass the domain entity(in this case Order) to repositories and it will be the
     repository implementations responsibility to convert the order entity objects into JPA entity objects and save into the DB.*/
    Order save(Order order);
    Optional<Order> findByTrackingId(TrackingId trackingId);
}
