package com.nexus.auction.application.port.out;

import com.nexus.auction.domain.model.Auction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuctionRepositoryPort {
    Auction save(Auction auction);
    Optional<Auction> findById(String id);
    Optional<Auction> findByIdForUpdate(String id);
    boolean existsActiveOrPendingForProduct(String productId);
    long countActiveOrPendingBySeller(String sellerId);
    List<Auction> search(String status, String sellerId, String productId, int page, int size);
    List<Auction> findPendingReadyToStart(Instant now);
    List<Auction> findActiveReadyToEnd(Instant now);
    List<Auction> findEndedAwaitingPaymentTimeout(Instant now);
}
