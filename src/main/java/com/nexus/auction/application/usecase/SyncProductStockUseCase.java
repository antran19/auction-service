package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.ProductStockRepositoryPort;

import java.time.Instant;

// Keeps auction-service's copy of each product's sellable stock in step with fulfillment's
// InventoryLevelChanged events; the event time travels with the level so an older event
// cannot overwrite a newer one.
public class SyncProductStockUseCase {

    private final ProductStockRepositoryPort repositoryPort;

    public SyncProductStockUseCase(ProductStockRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    public void onLevelChanged(String productId, int availableQuantity, Instant occurredAt) {
        repositoryPort.applyIfNewer(productId, availableQuantity, occurredAt);
    }
}
