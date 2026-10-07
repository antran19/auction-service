package com.nexus.auction.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPaymentRequest(@NotBlank String sessionId) {
}
