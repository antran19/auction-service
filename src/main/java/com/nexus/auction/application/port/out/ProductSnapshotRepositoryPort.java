package com.nexus.auction.application.port.out;

import com.nexus.auction.domain.model.AuctionProduct;

import java.util.Optional;

public interface ProductSnapshotRepositoryPort {
    Optional<AuctionProduct> findById(String productId);

    // No-op when the product is already known, so a redelivered ProductCreated cannot reset a
    // status a later ProductStatusChanged already moved on.
    void insertIfAbsent(AuctionProduct product);

    void save(AuctionProduct product);
}
