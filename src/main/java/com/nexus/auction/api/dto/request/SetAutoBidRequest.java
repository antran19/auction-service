package com.nexus.auction.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SetAutoBidRequest(
        @NotNull @DecimalMin(value = "0.01", message = "maxAmount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "maxAmount must have at most 10 digits and 2 decimals") BigDecimal maxAmount) {
}
