package com.food.ordering.system.order.service.domain.entity;

import com.food.ordering.system.domain.entity.AggregateRoot;
import com.food.ordering.system.domain.valueobject.CustomerId;

/* We have extended the AggregateRoot in this class as well just to follow the guidelines, although there is no other entity in this
aggregate. This class is empty, we just use it to check the existence of the customer.*/
public class Customer extends AggregateRoot<CustomerId> {
    private String username;
    private String firstName;
    private String lastName;

    public Customer() {}

    public Customer(CustomerId customerId) {
        super.setId(customerId);
    }

    public Customer(CustomerId customerId, String username, String firstName, String lastName) {
        super.setId(customerId);
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
