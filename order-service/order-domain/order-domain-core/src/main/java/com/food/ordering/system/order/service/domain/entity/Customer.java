package com.food.ordering.system.order.service.domain.entity;

import com.food.ordering.system.domain.entity.AggregateRoot;
import com.food.ordering.system.domain.valueobject.CustomerId;

/* We have extended the AggregateRoot in this class as well just to follow the guidelines, although there is no other entity in this
aggregate. This class is empty, we just use it to check the existence of the customer.*/
public class Customer extends AggregateRoot<CustomerId> {
}
