package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.infrastructure.persistence.entity.BidJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BidJpaRepository extends JpaRepository<BidJpaEntity, UUID> {
    List<BidJpaEntity> findByAuctionIdOrderByPlacedAtDesc(UUID auctionId, PageRequest pageRequest);

    // One row per auction with bids: [auctionId, number of bids, number of different bidders].
    @Query("SELECT b.auctionId, COUNT(b), COUNT(DISTINCT b.bidderId) FROM BidJpaEntity b "
            + "WHERE b.auctionId IN :auctionIds GROUP BY b.auctionId")
    List<Object[]> statsByAuctionIds(@Param("auctionIds") List<UUID> auctionIds);

    // One row per auction: [auctionId, highest amount, last bid time], most recent activity first.
    @Query("SELECT b.auctionId, MAX(b.amount), MAX(b.placedAt) FROM BidJpaEntity b WHERE b.bidderId = :bidderId "
            + "GROUP BY b.auctionId ORDER BY MAX(b.placedAt) DESC")
    List<Object[]> summarizeByBidder(@Param("bidderId") String bidderId, Pageable pageable);
}
