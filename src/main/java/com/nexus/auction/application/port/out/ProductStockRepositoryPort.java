package com.nexus.auction.application.port.out;

import java.time.Instant;

public interface ProductStockRepositoryPort {
    // 0 for a product whose stock was never reported.
    int availableQuantity(String productId);

    // Stores the level unless a level reported at a later time is already stored.
    void applyIfNewer(String productId, int availableQuantity, Instant asOf);
}
