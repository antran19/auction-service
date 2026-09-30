package com.nexus.auction.api;

import com.nexus.auction.api.dto.request.PlaceBidRequest;
import com.nexus.auction.api.dto.response.BidResponse;
import com.nexus.auction.api.mapper.AuctionApiMapper;
import com.nexus.auction.application.usecase.BidResult;
import com.nexus.auction.application.usecase.GetBidHistoryUseCase;
import com.nexus.auction.application.usecase.PlaceBidUseCase;
import com.nexus.common.core.ApiResponse;
import com.nexus.common.security.RequiresPrivilege;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/bids")
public class BidController {

    private final PlaceBidUseCase placeBidUseCase;
    private final GetBidHistoryUseCase getBidHistoryUseCase;
    private final AuctionApiMapper mapper;

    public BidController(PlaceBidUseCase placeBidUseCase, GetBidHistoryUseCase getBidHistoryUseCase,
                          AuctionApiMapper mapper) {
        this.placeBidUseCase = placeBidUseCase;
        this.getBidHistoryUseCase = getBidHistoryUseCase;
        this.mapper = mapper;
    }

    @RequiresPrivilege("AUCTION.BID")
    @PostMapping
    public ResponseEntity<ApiResponse<BidResponse>> placeBid(Authentication authentication,
                                                                @PathVariable String auctionId,
                                                                @Valid @RequestBody PlaceBidRequest request) {
        // bidderId MUST come from the JWT — same reasoning as sellerId in AuctionController.
        String bidderId = (String) authentication.getPrincipal();
        BidResult result = placeBidUseCase.placeBid(auctionId, bidderId, request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(mapper.toResponse(result)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BidResponse>>> history(
            @PathVariable String auctionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<BidResult> results = getBidHistoryUseCase.getHistory(auctionId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(results.stream().map(mapper::toResponse).toList()));
    }
}
