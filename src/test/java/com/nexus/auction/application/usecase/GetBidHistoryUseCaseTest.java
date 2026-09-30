package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.domain.model.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetBidHistoryUseCaseTest {

    private BidRepositoryPort bidRepositoryPort;
    private GetBidHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        bidRepositoryPort = mock(BidRepositoryPort.class);
        useCase = new GetBidHistoryUseCase(bidRepositoryPort);
    }

    @Test
    void getHistory_returnsBidsForTheAuction() {
        Bid bid = Bid.create("auction-1", "bidder-1", new BigDecimal("100.00"));
        when(bidRepositoryPort.findByAuctionId("auction-1", 0, 20)).thenReturn(List.of(bid));

        List<BidResult> results = useCase.getHistory("auction-1", 0, 20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).bidderId()).isEqualTo("bidder-1");
    }
}
