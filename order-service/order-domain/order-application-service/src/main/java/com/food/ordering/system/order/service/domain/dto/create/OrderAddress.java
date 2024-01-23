package com.food.ordering.system.order.service.domain.dto.create;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;

/* Here, you can apply more validation checks using validation annotations, like @Max() check. */
@Getter
@Builder
@AllArgsConstructor
public class OrderAddress {
    @NotNull
    @Max(value = 50)
    private final String street;
    @NotNull
    @Max(value = 10)
    private final String postalCode;
    @NotNull
    @Max(value = 50)
    private final String city;
}
