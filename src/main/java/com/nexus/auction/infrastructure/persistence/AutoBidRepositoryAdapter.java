package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.application.port.out.AutoBidRepositoryPort;
import com.nexus.auction.domain.model.AutoBid;
import com.nexus.auction.infrastructure.persistence.entity.AutoBidJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AutoBidRepositoryAdapter implements AutoBidRepositoryPort {

    private final AutoBidJpaRepository jpaRepository;

    public AutoBidRepositoryAdapter(AutoBidJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AutoBid save(AutoBid autoBid) {
        jpaRepository.save(new AutoBidJpaEntity(UUID.fromString(autoBid.getId()),
                UUID.fromString(autoBid.getAuctionId()), autoBid.getBidderId(), autoBid.getMaxAmount(),
                autoBid.getCreatedAt(), autoBid.getUpdatedAt()));
        return autoBid;
    }

    @Override
    public Optional<AutoBid> findByAuctionIdAndBidderId(String auctionId, String bidderId) {
        return UuidIds.tryParse(auctionId)
                .flatMap(uuid -> jpaRepository.findByAuctionIdAndBidderId(uuid, bidderId))
                .map(this::toDomain);
    }

    @Override
    public List<AutoBid> findByAuctionId(String auctionId) {
        return UuidIds.tryParse(auctionId)
                .map(uuid -> jpaRepository.findByAuctionIdOrderByCreatedAtAsc(uuid).stream().map(this::toDomain).toList())
                .orElse(List.of());
    }

    @Override
    public List<AutoBid> findByBidderIdAndAuctionIds(String bidderId, List<String> auctionIds) {
        List<UUID> uuids = auctionIds.stream().map(UuidIds::tryParse).flatMap(Optional::stream).toList();
        if (uuids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByBidderIdAndAuctionIdIn(bidderId, uuids).stream().map(this::toDomain).toList();
    }

    private AutoBid toDomain(AutoBidJpaEntity e) {
        return AutoBid.reconstitute(e.getId().toString(), e.getAuctionId().toString(), e.getBidderId(),
                e.getMaxAmount(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
