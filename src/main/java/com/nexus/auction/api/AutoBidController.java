package com.nexus.auction.api;

import com.nexus.auction.api.dto.request.SetAutoBidRequest;
import com.nexus.auction.api.dto.response.AutoBidResponse;
import com.nexus.auction.application.usecase.AutoBidResult;
import com.nexus.auction.application.usecase.SetAutoBidUseCase;
import com.nexus.common.core.ApiResponse;
import com.nexus.common.security.RequiresPrivilege;
import com.nexus.common.security.TokenDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/auto-bid")
public class AutoBidController {

    private final SetAutoBidUseCase setAutoBidUseCase;

    public AutoBidController(SetAutoBidUseCase setAutoBidUseCase) {
        this.setAutoBidUseCase = setAutoBidUseCase;
    }

    // PUT because the call sets "my maximum on this auction": sending it again replaces it (a
    // higher value raises it, the same value changes nothing, a lower one is refused).
    @RequiresPrivilege("AUCTION.BID")
    @PutMapping
    public ResponseEntity<ApiResponse<AutoBidResponse>> setAutoBid(Authentication authentication,
                                                                    @PathVariable String auctionId,
                                                                    @Valid @RequestBody SetAutoBidRequest request) {
        // bidderId MUST come from the JWT, like for a manual bid.
        String bidderId = (String) authentication.getPrincipal();
        String trustLevel = authentication.getDetails() instanceof TokenDetails details ? details.trustLevel() : null;
        AutoBidResult result = setAutoBidUseCase.setAutoBid(auctionId, bidderId, request.maxAmount(), trustLevel);
        return ResponseEntity.ok(ApiResponse.ok(AutoBidResponse.from(result)));
    }
}
