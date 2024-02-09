package com.food.ordering.system.order.service.domain;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {
    /* What does this method do?
    Remember, in the domain-core, we haven't added any spring dep. So the OrderDomainService implementation(OrderDomainServiceImpl)
    is not marked as a spring bean in the order-domain-core module.
    But we still want to use the OrderDomainService as a spring bean and inject it to the order-application service module.
    To use the OrderDomainService as a spring bean, we need to register it as a bean and we do it here. So when the spring boot application
    starts, it will register the OrderDomainService as a spring bean, although we don't have a dep to the spring on the order-domain-core.*/
    @Bean
    public OrderDomainService orderDomainService() {
        return new OrderDomainServiceImpl();
    }
}
