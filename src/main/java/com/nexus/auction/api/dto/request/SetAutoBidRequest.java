package com.nexus.auction.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SetAutoBidRequest(
        @NotNull @DecimalMin(value = "0.01", message = "maxAmount must be greater than zero") BigDecimal maxAmount) {
}
