package com.nexus.auction.infrastructure.config;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.application.usecase.AdminCancelAuctionUseCase;
import com.nexus.auction.application.usecase.CancelAuctionUseCase;
import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.usecase.CreateAuctionUseCase;
import com.nexus.auction.application.usecase.GetAuctionUseCase;
import com.nexus.auction.application.usecase.GetBidHistoryUseCase;
import com.nexus.auction.application.usecase.ListAuctionsUseCase;
import com.nexus.auction.application.usecase.PlaceBidUseCase;
import com.nexus.auction.application.usecase.UpdateAuctionUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public CreateAuctionUseCase createAuctionUseCase(AuctionRepositoryPort auctionPort,
                                                       EventPublisherPort eventPublisherPort) {
        return new CreateAuctionUseCase(auctionPort, eventPublisherPort);
    }

    @Bean
    public UpdateAuctionUseCase updateAuctionUseCase(AuctionRepositoryPort auctionPort) {
        return new UpdateAuctionUseCase(auctionPort);
    }

    @Bean
    public CancelAuctionUseCase cancelAuctionUseCase(AuctionRepositoryPort auctionPort,
                                                       EventPublisherPort eventPublisherPort) {
        return new CancelAuctionUseCase(auctionPort, eventPublisherPort);
    }

    @Bean
    public AdminCancelAuctionUseCase adminCancelAuctionUseCase(AuctionRepositoryPort auctionPort,
                                                                 EventPublisherPort eventPublisherPort) {
        return new AdminCancelAuctionUseCase(auctionPort, eventPublisherPort);
    }

    @Bean
    public PlaceBidUseCase placeBidUseCase(AuctionRepositoryPort auctionPort, BidRepositoryPort bidPort,
                                            EventPublisherPort eventPublisherPort) {
        return new PlaceBidUseCase(auctionPort, bidPort, eventPublisherPort);
    }

    @Bean
    public GetAuctionUseCase getAuctionUseCase(AuctionRepositoryPort auctionPort) {
        return new GetAuctionUseCase(auctionPort);
    }

    @Bean
    public ListAuctionsUseCase listAuctionsUseCase(AuctionRepositoryPort auctionPort) {
        return new ListAuctionsUseCase(auctionPort);
    }

    @Bean
    public GetBidHistoryUseCase getBidHistoryUseCase(BidRepositoryPort bidPort) {
        return new GetBidHistoryUseCase(bidPort);
    }
}
