package com.food.ordering.system.order.service.domain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/* scanBasePackages property is important when working with multiple modules. With this property, any package on the other modules
will be scanned as long as it starts with the value of this property("com.food.ordering.system") as the package name.

The value of @EntityScan's basePackages is pointing only to the module that holds jpa entities. So only the entities that are in that
package, will be scanned as JPA entities.

With @EnableJpaRepositories(basePackages = "") any JPA class that is in that package, will be scanned as a JPA repository.*/
@EnableJpaRepositories(basePackages = { "com.food.ordering.system.order.service.dataaccess", "com.food.ordering.system.dataaccess" })
@EntityScan(basePackages = { "com.food.ordering.system.order.service.dataaccess", "com.food.ordering.system.dataaccess" })
@SpringBootApplication(scanBasePackages = "com.food.ordering.system")
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
