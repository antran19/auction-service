package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.application.port.out.AuctionBidStats;
import com.nexus.auction.application.port.out.BidderAuctionSummary;
import com.nexus.auction.domain.model.Bid;
import com.nexus.auction.infrastructure.persistence.entity.BidJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BidRepositoryAdapter implements BidRepositoryPort {

    private final BidJpaRepository jpaRepository;

    public BidRepositoryAdapter(BidJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Bid save(Bid bid) {
        BidJpaEntity entity = new BidJpaEntity(UUID.fromString(bid.getId()), UUID.fromString(bid.getAuctionId()),
                bid.getBidderId(), bid.getAmount(), bid.getPlacedAt());
        jpaRepository.save(entity);
        return bid;
    }

    @Override
    public List<Bid> findByAuctionId(String auctionId, int page, int size) {
        return UuidIds.tryParse(auctionId)
                .map(uuid -> jpaRepository.findByAuctionIdOrderByPlacedAtDesc(uuid, PageRequest.of(page, size))
                        .stream().map(this::toDomain).toList())
                .orElse(List.of());
    }

    @Override
    public List<BidderAuctionSummary> findAuctionsBidOnBy(String bidderId, int page, int size) {
        return jpaRepository.summarizeByBidder(bidderId, PageRequest.of(page, size)).stream()
                .map(row -> new BidderAuctionSummary(row[0].toString(), (BigDecimal) row[1], (Instant) row[2]))
                .toList();
    }

    @Override
    public List<AuctionBidStats> statsByAuctionIds(List<String> auctionIds) {
        List<UUID> uuids = auctionIds.stream().map(UuidIds::tryParse).flatMap(Optional::stream).toList();
        if (uuids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.statsByAuctionIds(uuids).stream()
                .map(row -> new AuctionBidStats(row[0].toString(), ((Number) row[1]).longValue(), ((Number) row[2]).longValue()))
                .toList();
    }

    private Bid toDomain(BidJpaEntity e) {
        return Bid.reconstitute(e.getId().toString(), e.getAuctionId().toString(), e.getBidderId(),
                e.getAmount(), e.getPlacedAt());
    }
}
