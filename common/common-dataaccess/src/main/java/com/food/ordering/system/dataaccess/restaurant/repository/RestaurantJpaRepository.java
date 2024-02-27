package com.food.ordering.system.dataaccess.restaurant.repository;

import com.food.ordering.system.dataaccess.restaurant.entity.RestaurantEntity;
import com.food.ordering.system.dataaccess.restaurant.entity.RestaurantEntityId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* note: The identifier of RestaurantEntity is RestaurantEntityId. */
@Repository
public interface RestaurantJpaRepository extends JpaRepository<RestaurantEntity, RestaurantEntityId> {
    /* Since we pass a LIST of product ids, we use productIdIn(look at `in`) in the method name. This means, this will be converted
    to an sql query using the `IN` statement. */
    Optional<List<RestaurantEntity>> findByRestaurantIdAndProductIdIn(UUID restaurantId, List<UUID> productIds);
}
