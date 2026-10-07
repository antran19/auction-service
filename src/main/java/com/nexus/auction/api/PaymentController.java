package com.nexus.auction.api;

import com.nexus.auction.api.dto.request.ConfirmPaymentRequest;
import com.nexus.auction.api.dto.request.CreateCheckoutSessionRequest;
import com.nexus.auction.api.dto.response.AuctionResponse;
import com.nexus.auction.api.dto.response.CheckoutSessionResponse;
import com.nexus.auction.api.mapper.AuctionApiMapper;
import com.nexus.auction.application.port.out.PaymentGatewayPort;
import com.nexus.auction.application.usecase.AuctionResult;
import com.nexus.auction.application.usecase.ConfirmPaymentUseCase;
import com.nexus.auction.application.usecase.CreateCheckoutSessionUseCase;
import com.nexus.common.core.ApiResponse;
import com.nexus.common.security.RequiresPrivilege;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auctions/{auctionId}")
public class PaymentController {

    private final CreateCheckoutSessionUseCase createCheckoutSessionUseCase;
    private final ConfirmPaymentUseCase confirmPaymentUseCase;
    private final AuctionApiMapper mapper;

    public PaymentController(CreateCheckoutSessionUseCase createCheckoutSessionUseCase,
                              ConfirmPaymentUseCase confirmPaymentUseCase,
                              AuctionApiMapper mapper) {
        this.createCheckoutSessionUseCase = createCheckoutSessionUseCase;
        this.confirmPaymentUseCase = confirmPaymentUseCase;
        this.mapper = mapper;
    }

    // Reuses AUCTION.BID -- paying for what you won is the natural extension of the bidding
    // capability every BUYER already has by default; it does not warrant its own privilege.
    @RequiresPrivilege("AUCTION.BID")
    @PostMapping("/checkout-session")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> createCheckoutSession(
            Authentication authentication, @PathVariable String auctionId,
            @Valid @RequestBody CreateCheckoutSessionRequest request) {
        String callerId = callerId(authentication);
        PaymentGatewayPort.CheckoutSession session = createCheckoutSessionUseCase.createSession(
                auctionId, callerId, request.successUrl(), request.cancelUrl());
        return ResponseEntity.ok(ApiResponse.ok(mapper.toResponse(session)));
    }

    @RequiresPrivilege("AUCTION.BID")
    @PostMapping("/confirm-payment")
    public ResponseEntity<ApiResponse<AuctionResponse>> confirmPayment(
            Authentication authentication, @PathVariable String auctionId,
            @Valid @RequestBody ConfirmPaymentRequest request) {
        String callerId = callerId(authentication);
        AuctionResult result = confirmPaymentUseCase.confirm(auctionId, callerId, request.sessionId());
        return ResponseEntity.ok(ApiResponse.ok(mapper.toResponse(result)));
    }

    private static String callerId(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }
}
