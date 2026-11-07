package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.infrastructure.persistence.entity.AutoBidJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutoBidJpaRepository extends JpaRepository<AutoBidJpaEntity, UUID> {
    Optional<AutoBidJpaEntity> findByAuctionIdAndBidderId(UUID auctionId, String bidderId);
    List<AutoBidJpaEntity> findByAuctionIdOrderByCreatedAtAsc(UUID auctionId);
    List<AutoBidJpaEntity> findByBidderIdAndAuctionIdIn(String bidderId, List<UUID> auctionIds);
}
