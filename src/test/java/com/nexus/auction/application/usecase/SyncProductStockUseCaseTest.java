package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.ProductStockRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SyncProductStockUseCaseTest {

    private final ProductStockRepositoryPort repositoryPort = mock(ProductStockRepositoryPort.class);
    private final SyncProductStockUseCase useCase = new SyncProductStockUseCase(repositoryPort);

    @Test
    void onLevelChanged_storesTheLevelWithTheEventTime() {
        Instant at = Instant.parse("2026-10-17T05:00:00Z");

        useCase.onLevelChanged("product-1", 4, at);

        verify(repositoryPort).applyIfNewer("product-1", 4, at);
    }
}
