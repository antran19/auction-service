package com.nexus.auction.application.usecase;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAuctionUseCaseTest {

    private AuctionRepositoryPort auctionRepositoryPort;
    private GetAuctionUseCase useCase;

    @BeforeEach
    void setUp() {
        auctionRepositoryPort = mock(AuctionRepositoryPort.class);
        useCase = new GetAuctionUseCase(auctionRepositoryPort);
    }

    @Test
    void get_returnsTheAuction() {
        Auction auction = Auction.create("product-1", "seller-1", new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
        when(auctionRepositoryPort.findById(auction.getId())).thenReturn(Optional.of(auction));

        AuctionResult result = useCase.get(auction.getId());

        assertThat(result.id()).isEqualTo(auction.getId());
    }

    @Test
    void get_throwsNotFoundForUnknownId() {
        when(auctionRepositoryPort.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get("missing")).isInstanceOf(AuctionNotFoundException.class);
    }
}
