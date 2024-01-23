package com.food.ordering.system.order.service.domain.dto.track;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import javax.validation.constraints.NotNull;
import java.util.UUID;

/* The clients will use this to query the latest state of an order. */
@Getter
@Builder
@AllArgsConstructor
public class TrackOrderQuery {
    @NotNull
    private final UUID orderTrackingId;
}
