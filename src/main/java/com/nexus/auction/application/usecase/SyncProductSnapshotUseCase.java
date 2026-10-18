package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.ProductSnapshotRepositoryPort;
import com.nexus.auction.domain.model.AuctionProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Keeps auction-service's copy of product owner and status in step with catalog-events.
public class SyncProductSnapshotUseCase {

    private static final Logger log = LoggerFactory.getLogger(SyncProductSnapshotUseCase.class);

    private final ProductSnapshotRepositoryPort repositoryPort;

    public SyncProductSnapshotUseCase(ProductSnapshotRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    public void onCreated(String productId, String sellerId) {
        // Events from before ProductCreated carried a seller (common-libs < 1.11.0) cannot be
        // recorded; such a product cannot be auctioned.
        if (sellerId == null) {
            log.warn("ProductCreated for {} has no seller; product cannot be auctioned", productId);
            return;
        }
        repositoryPort.insertIfAbsent(AuctionProduct.create(productId, sellerId));
    }

    public void onStatusChanged(String productId, String newStatus) {
        repositoryPort.findById(productId).ifPresentOrElse(
                p -> repositoryPort.save(p.withStatus(newStatus)),
                () -> log.warn("Status change for {} ignored: product unknown to auction-service", productId));
    }
}
