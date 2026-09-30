package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.application.port.out.BidRepositoryPort;
import com.nexus.auction.domain.model.Bid;
import com.nexus.auction.infrastructure.persistence.entity.BidJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
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

    private Bid toDomain(BidJpaEntity e) {
        return Bid.reconstitute(e.getId().toString(), e.getAuctionId().toString(), e.getBidderId(),
                e.getAmount(), e.getPlacedAt());
    }
}
