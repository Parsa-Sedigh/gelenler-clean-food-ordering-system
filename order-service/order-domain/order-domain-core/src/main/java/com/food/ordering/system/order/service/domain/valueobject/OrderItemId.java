package com.food.ordering.system.order.service.domain.valueobject;

import com.food.ordering.system.domain.valueobject.BaseId;

/* The uniqueness of an OrderItem is only important in the aggregates. So we don't need a UUID as the BaseId. Having a `Long` value
starting from one will be enough. An OrderItem will be inside an Order(which has UUID as it's id), so we don't need the id of
OrderItem to be a UUID. */
public class OrderItemId extends BaseId<Long> {
    public OrderItemId(Long value) {
        super(value);
    }
}
