package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.infrastructure.persistence.entity.BidJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BidJpaRepository extends JpaRepository<BidJpaEntity, UUID> {
    List<BidJpaEntity> findByAuctionIdOrderByPlacedAtDesc(UUID auctionId, PageRequest pageRequest);
}
