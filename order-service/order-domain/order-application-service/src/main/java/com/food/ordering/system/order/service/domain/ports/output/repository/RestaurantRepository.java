package com.food.ordering.system.order.service.domain.ports.output.repository;

import com.food.ordering.system.order.service.domain.entity.Restaurant;

import java.util.Optional;

public interface RestaurantRepository {
    /* we pass the id of the product in the Restaurant object and we expect to return the details of the products including
     the name and price*/
    Optional<Restaurant> findRestaurantInformation(Restaurant restaurant);

}
