package com.food.ordering.system.restaurant.service.ports.input.message.listener;

import com.food.ordering.system.restaurant.service.dto.RestaurantApprovalRequest;

public interface RestaurantApprovalRequestMessageListener {
    void approveOrder(RestaurantApprovalRequest restaurantApprovalRequest);
}
