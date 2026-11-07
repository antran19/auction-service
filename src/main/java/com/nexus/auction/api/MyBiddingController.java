package com.nexus.auction.api;

import com.nexus.auction.api.dto.response.MyBiddingResponse;
import com.nexus.auction.application.usecase.ListMyBiddingUseCase;
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
@RequestMapping("/api/v1/auctions/mine/bidding")
public class MyBiddingController {

    private final ListMyBiddingUseCase listMyBiddingUseCase;

    public MyBiddingController(ListMyBiddingUseCase listMyBiddingUseCase) {
        this.listMyBiddingUseCase = listMyBiddingUseCase;
    }

    // GET is public for the rest of /auctions, so this one asks for the privilege itself. The person
    // is the one in the token: there is no way to ask for somebody else's list.
    @RequiresPrivilege("AUCTION.BID")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MyBiddingResponse>>> myBidding(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String bidderId = (String) authentication.getPrincipal();
        List<MyBiddingResponse> items = listMyBiddingUseCase.list(bidderId, page, size).stream()
                .map(MyBiddingResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(items));
    }
}
