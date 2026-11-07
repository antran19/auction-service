package com.nexus.auction.api.dto.response;

import com.nexus.auction.application.usecase.MyBiddingItem;

import java.math.BigDecimal;
import java.time.Instant;

public record MyBiddingResponse(String auctionId, String productId, String auctionStatus, String myStatus,
                                Instant endTime, BigDecimal currentHighestBid, BigDecimal myHighestBid,
                                BigDecimal myMaxAutoBid) {

    public static MyBiddingResponse from(MyBiddingItem item) {
        return new MyBiddingResponse(item.auctionId(), item.productId(), item.auctionStatus(), item.myStatus(),
                item.endTime(), item.currentHighestBid(), item.myHighestBid(), item.myMaxAutoBid());
    }
}
