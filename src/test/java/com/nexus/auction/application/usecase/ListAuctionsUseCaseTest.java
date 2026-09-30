package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListAuctionsUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private ListAuctionsUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        useCase = new ListAuctionsUseCase(auctionRepositoryPort);
    }

    @Test
    void list_delegatesFiltersToTheRepository() {
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
        when(auctionRepositoryPort.search("ACTIVE", "seller-1", null, 0, 20)).thenReturn(List.of(auction));

        List<AuctionResult> results = useCase.list(new AuctionSearchQuery("ACTIVE", "seller-1", null, 0, 20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(auction.getId());
    }
}
