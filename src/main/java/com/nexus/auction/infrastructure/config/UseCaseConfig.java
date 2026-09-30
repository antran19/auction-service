package com.nexus.auction.infrastructure.config;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.application.usecase.AdminCancelAuctionUseCase;
import com.nexus.auction.application.usecase.CancelAuctionUseCase;
import com.nexus.auction.application.usecase.CreateAuctionUseCase;
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
}
