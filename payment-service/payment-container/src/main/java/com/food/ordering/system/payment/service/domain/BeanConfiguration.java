package com.food.ordering.system.payment.service.domain;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {
    /* creates a spring bean from the PaymentDomainServiceImpl(returns PaymentDomainService interface).
    Remember: We haven't put the spring dep in core domain layer of payment svc but we still want to inject and use the
    PaymentDomainService from other modules. So we create a bean config in this container module.*/
    @Bean
    public PaymentDomainService paymentDomainService() {
        return new PaymentDomainServiceImpl();
    }
}
