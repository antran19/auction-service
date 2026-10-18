package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.ProductSnapshotRepositoryPort;
import com.nexus.auction.domain.model.AuctionProduct;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyncProductSnapshotUseCaseTest {

    private final ProductSnapshotRepositoryPort repositoryPort = mock(ProductSnapshotRepositoryPort.class);
    private final SyncProductSnapshotUseCase useCase = new SyncProductSnapshotUseCase(repositoryPort);

    @Test
    void onCreated_insertsADraftProductOwnedByTheSeller() {
        useCase.onCreated("product-1", "seller-1");

        ArgumentCaptor<AuctionProduct> captor = ArgumentCaptor.forClass(AuctionProduct.class);
        verify(repositoryPort).insertIfAbsent(captor.capture());
        assertThat(captor.getValue().getSellerId()).isEqualTo("seller-1");
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().isAuctionable()).isFalse();
    }

    @Test
    void onCreated_skipsAnOldEventWithoutSeller() {
        useCase.onCreated("product-1", null);

        verify(repositoryPort, never()).insertIfAbsent(any());
    }

    @Test
    void onStatusChanged_updatesAKnownProduct_andIgnoresAnUnknownOne() {
        when(repositoryPort.findById("product-1")).thenReturn(Optional.of(AuctionProduct.create("product-1", "seller-1")));
        when(repositoryPort.findById("unknown")).thenReturn(Optional.empty());

        useCase.onStatusChanged("product-1", "ACTIVE");
        useCase.onStatusChanged("unknown", "ACTIVE");

        ArgumentCaptor<AuctionProduct> captor = ArgumentCaptor.forClass(AuctionProduct.class);
        verify(repositoryPort, times(1)).save(captor.capture());
        assertThat(captor.getValue().isAuctionable()).isTrue();
    }
}
