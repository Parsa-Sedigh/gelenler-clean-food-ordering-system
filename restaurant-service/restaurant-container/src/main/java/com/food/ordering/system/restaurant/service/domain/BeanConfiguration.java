package com.food.ordering.system.restaurant.service.domain;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {
    /* Note: We haven't put the spring dep in the restaurant core domain, but we still want to inject the RestaurantDomainService into
    the other modules. So here(in container module), we create a Bean configuration. */
    @Bean
    public RestaurantDomainService restaurantDomainService() {
        return new RestaurantDomainServiceImpl();
    }
}
