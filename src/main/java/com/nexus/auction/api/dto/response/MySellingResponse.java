package com.nexus.auction.api.dto.response;

import com.nexus.auction.application.usecase.MySellingItem;

import java.math.BigDecimal;
import java.time.Instant;

public record MySellingResponse(String auctionId, String productId, String productName, String status,
                                BigDecimal startingPrice, BigDecimal bidIncrement, BigDecimal currentHighestBid,
                                Instant startTime, Instant endTime, long bidCount, long bidderCount,
                                String winnerId, BigDecimal finalPrice, Instant paymentDeadline, Instant paidAt) {

    public static MySellingResponse from(MySellingItem item) {
        return new MySellingResponse(item.auctionId(), item.productId(), item.productName(), item.status(),
                item.startingPrice(), item.bidIncrement(), item.currentHighestBid(), item.startTime(), item.endTime(),
                item.bidCount(), item.bidderCount(), item.winnerId(), item.finalPrice(), item.paymentDeadline(),
                item.paidAt());
    }
}
