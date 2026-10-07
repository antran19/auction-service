package com.nexus.auction.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCheckoutSessionRequest(@NotBlank String successUrl, @NotBlank String cancelUrl) {
}
