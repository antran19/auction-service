package com.nexus.auction.api;

import com.nexus.auction.api.dto.response.MySellingResponse;
import com.nexus.auction.application.usecase.ListMySellingUseCase;
import com.nexus.common.core.ApiResponse;
import com.nexus.common.security.RequiresPrivilege;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions/mine/selling")
public class MySellingController {

    private final ListMySellingUseCase listMySellingUseCase;

    public MySellingController(ListMySellingUseCase listMySellingUseCase) {
        this.listMySellingUseCase = listMySellingUseCase;
    }

    // The seller is the one in the token: there is no way to ask for somebody else's auctions. The privilege
    // is the one needed to create an auction, so only sellers get a list.
    @RequiresPrivilege("AUCTION.CREATE")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MySellingResponse>>> mySelling(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String sellerId = (String) authentication.getPrincipal();
        List<MySellingResponse> items = listMySellingUseCase.list(sellerId, status, page, size).stream()
                .map(MySellingResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }
}
