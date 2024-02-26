package com.food.ordering.system.restaurant.service.domain.ports.input.message.listener;

import com.food.ordering.system.restaurant.service.domain.dto.RestaurantApprovalRequest;

public interface RestaurantApprovalMessageListener {
    void approveOrder(RestaurantApprovalRequest restaurantApprovalRequest);
}
