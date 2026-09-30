package com.nexus.auction.application.usecase;

public record AuctionSearchQuery(String status, String sellerId, String productId, int page, int size) {
}
