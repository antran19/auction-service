package com.nexus.auction.api;

import com.nexus.auction.api.dto.request.CreateAuctionRequest;
import com.nexus.auction.api.dto.request.UpdateAuctionRequest;
import com.nexus.auction.api.dto.response.AuctionResponse;
import com.nexus.auction.api.mapper.AuctionApiMapper;
import com.nexus.auction.application.usecase.*;
import com.nexus.common.core.ApiResponse;
import com.nexus.common.security.RequiresPrivilege;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final CreateAuctionUseCase createAuctionUseCase;
    private final UpdateAuctionUseCase updateAuctionUseCase;
    private final CancelAuctionUseCase cancelAuctionUseCase;
    private final AdminCancelAuctionUseCase adminCancelAuctionUseCase;
    private final GetAuctionUseCase getAuctionUseCase;
    private final ListAuctionsUseCase listAuctionsUseCase;
    private final AuctionApiMapper mapper;

    public AuctionController(CreateAuctionUseCase createAuctionUseCase, UpdateAuctionUseCase updateAuctionUseCase,
                              CancelAuctionUseCase cancelAuctionUseCase, AdminCancelAuctionUseCase adminCancelAuctionUseCase,
                              GetAuctionUseCase getAuctionUseCase, ListAuctionsUseCase listAuctionsUseCase,
                              AuctionApiMapper mapper) {
        this.createAuctionUseCase = createAuctionUseCase;
        this.updateAuctionUseCase = updateAuctionUseCase;
        this.cancelAuctionUseCase = cancelAuctionUseCase;
        this.adminCancelAuctionUseCase = adminCancelAuctionUseCase;
        this.getAuctionUseCase = getAuctionUseCase;
        this.listAuctionsUseCase = listAuctionsUseCase;
        this.mapper = mapper;
    }

    @RequiresPrivilege("AUCTION.CREATE")
    @PostMapping
    public ResponseEntity<ApiResponse<AuctionResponse>> create(Authentication authentication,
                                                                 @Valid @RequestBody CreateAuctionRequest request) {
        // sellerId MUST come from the JWT, never the request body — same reasoning as
        // catalog-service's CreateProductUseCase: a client must not create an auction under
        // another seller's identity.
        String sellerId = callerId(authentication);
        CreateAuctionCommand command = new CreateAuctionCommand(request.productId(), sellerId,
                request.startingPrice(), request.bidIncrement(), request.startTime(), request.endTime());
        AuctionResult result = createAuctionUseCase.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(mapper.toResponse(result)));
    }

    @RequiresPrivilege("AUCTION.UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AuctionResponse>> update(Authentication authentication,
                                                                 @PathVariable String id,
                                                                 @Valid @RequestBody UpdateAuctionRequest request) {
        AuctionResult result = updateAuctionUseCase.update(id, request.startingPrice(), request.bidIncrement(),
                request.startTime(), request.endTime(), callerId(authentication));
        return ResponseEntity.ok(ApiResponse.ok(mapper.toResponse(result)));
    }

    @RequiresPrivilege("AUCTION.CANCEL")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<AuctionResponse>> cancel(Authentication authentication, @PathVariable String id) {
        AuctionResult result = cancelAuctionUseCase.cancel(id, callerId(authentication));
        return ResponseEntity.ok(ApiResponse.ok(mapper.toResponse(result)));
    }

    @RequiresPrivilege("AUCTION.ADMIN_CANCEL")
    @PostMapping("/{id}/admin-cancel")
    public ResponseEntity<ApiResponse<AuctionResponse>> adminCancel(Authentication authentication, @PathVariable String id) {
        AuctionResult result = adminCancelAuctionUseCase.cancel(id, callerId(authentication));
        return ResponseEntity.ok(ApiResponse.ok(mapper.toResponse(result)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuctionResponse>> get(@PathVariable String id) {
        AuctionResult result = getAuctionUseCase.get(id);
        return ResponseEntity.ok(ApiResponse.ok(mapper.toResponse(result)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuctionResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sellerId,
            @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<AuctionResult> results = listAuctionsUseCase.list(new AuctionSearchQuery(status, sellerId, productId, page, size));
        return ResponseEntity.ok(ApiResponse.ok(results.stream().map(mapper::toResponse).toList()));
    }

    private static String callerId(Authentication authentication) {
        return (String) authentication.getPrincipal();
    }
}
