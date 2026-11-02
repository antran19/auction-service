package com.nexus.auction.api;

import com.nexus.auction.application.usecase.GetAuctionUseCase;
import com.nexus.auction.infrastructure.streaming.SseBidBroadcaster;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Public like every GET of this service: a visitor can watch an auction without logging in.
@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/stream")
public class AuctionStreamController {

    private final GetAuctionUseCase getAuctionUseCase;
    private final SseBidBroadcaster broadcaster;

    public AuctionStreamController(GetAuctionUseCase getAuctionUseCase, SseBidBroadcaster broadcaster) {
        this.getAuctionUseCase = getAuctionUseCase;
        this.broadcaster = broadcaster;
    }

    // Each accepted bid arrives as an SSE event named "bid" whose data is the same JSON as one
    // item of the bid history. The auction is looked up first, so an unknown id answers 404 as
    // usual instead of opening a stream that never produces anything.
    @GetMapping
    public SseEmitter stream(@PathVariable String auctionId) {
        getAuctionUseCase.get(auctionId);
        return broadcaster.subscribe(auctionId);
    }
}
