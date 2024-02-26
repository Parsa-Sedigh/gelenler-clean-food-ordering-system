package com.food.ordering.system.domain.valueobject;

/* There is only a PAID option here. Because we only trigger restaurant service with a paid order.
Remember: For the response object of the restaurant service, we use OrderApprovalStatus enum. */
public enum RestaurantOrderStatus {
    PAID
}
