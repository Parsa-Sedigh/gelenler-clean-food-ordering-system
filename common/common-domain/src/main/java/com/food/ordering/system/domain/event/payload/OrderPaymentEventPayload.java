package com.food.ordering.system.domain.event.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/* json representation of the domain event for payment.

We create a json obj from this class and set that obj as a string val to the payload field of the OrderPaymentOutbox message. */
@Getter
@Builder
@AllArgsConstructor
public class OrderPaymentEventPayload {
    @JsonProperty
    private String id;
    @JsonProperty
    private String sagId;
    @JsonProperty
    private String orderId;
    @JsonProperty
    private String customerId;
    @JsonProperty
    private BigDecimal price;
    @JsonProperty
    private ZonedDateTime createdAt;
    @JsonProperty
    private String paymentOrderStatus;
}
