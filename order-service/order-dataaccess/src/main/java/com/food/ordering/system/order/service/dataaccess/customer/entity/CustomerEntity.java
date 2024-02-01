package com.food.ordering.system.order.service.dataaccess.customer.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

/* We use a materialized view from the customer DB to query the customer information in the order domain layer.

Note: We use this entity just to check if customer exists or not(simplified logic). So we don't add any other fields. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "order_customer_m_view", schema = "customer")
@Entity
public class CustomerEntity {
    @Id
    private UUID id;
}
