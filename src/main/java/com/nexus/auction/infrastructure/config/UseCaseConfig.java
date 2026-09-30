package com.nexus.auction.infrastructure.config;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.application.usecase.CreateAuctionUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public CreateAuctionUseCase createAuctionUseCase(AuctionRepositoryPort auctionPort,
                                                       EventPublisherPort eventPublisherPort) {
        return new CreateAuctionUseCase(auctionPort, eventPublisherPort);
    }
}
