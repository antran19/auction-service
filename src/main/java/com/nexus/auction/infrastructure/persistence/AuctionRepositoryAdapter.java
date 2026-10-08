package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import com.nexus.auction.infrastructure.persistence.entity.AuctionJpaEntity;
import com.nexus.common.core.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AuctionRepositoryAdapter implements AuctionRepositoryPort {

    private static final List<String> ACTIVE_OR_PENDING =
            List.of(AuctionStatus.PENDING.name(), AuctionStatus.ACTIVE.name());

    private final AuctionJpaRepository jpaRepository;

    public AuctionRepositoryAdapter(AuctionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Auction save(Auction auction) {
        AuctionJpaEntity entity = toEntity(auction);
        try {
            jpaRepository.save(entity);
            // Without an explicit flush, Hibernate can defer the actual INSERT past this
            // method returning (to the enclosing @Transactional method's commit) — by then
            // this try/catch is out of scope and the constraint violation would surface
            // as a late, untranslated commit-time failure instead of the ConflictException
            // below.
            jpaRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // Translates the partial unique index violation (V4 migration) into the same
            // domain error CreateAuctionUseCase's own check-then-insert already throws for
            // the non-racing case, instead of letting a raw persistence exception surface
            // as an unmapped 500.
            throw new ConflictException("PRODUCT_ALREADY_IN_AUCTION",
                    "Product already has an active or pending auction: " + auction.getProductId());
        }
        return auction;
    }

    @Override
    public Optional<Auction> findById(String id) {
        return UuidIds.tryParse(id).flatMap(jpaRepository::findById).map(this::toDomain);
    }

    @Override
    public Optional<Auction> findByIdForUpdate(String id) {
        return UuidIds.tryParse(id).flatMap(jpaRepository::findByIdForUpdate).map(this::toDomain);
    }

    @Override
    public boolean existsActiveOrPendingForProduct(String productId) {
        return UuidIds.tryParse(productId)
                .map(uuid -> jpaRepository.existsByProductIdAndStatusIn(uuid, ACTIVE_OR_PENDING))
                .orElse(false);
    }

    @Override
    public long countActiveOrPendingBySeller(String sellerId) {
        return jpaRepository.countBySellerIdAndStatusIn(sellerId, ACTIVE_OR_PENDING);
    }

    @Override
    public List<Auction> search(String status, String sellerId, String productId, int page, int size) {
        int offset = page * size;
        return jpaRepository.search(status, sellerId, productId, size, offset).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<Auction> findPendingReadyToStart(Instant now) {
        return jpaRepository.findByStatusAndStartTimeLessThanEqual(AuctionStatus.PENDING.name(), now).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<Auction> findActiveReadyToEnd(Instant now) {
        return jpaRepository.findByStatusAndEndTimeLessThanEqual(AuctionStatus.ACTIVE.name(), now).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<Auction> findEndedAwaitingPaymentTimeout(Instant now) {
        return jpaRepository.findByStatusAndWinnerIdIsNotNullAndPaymentTimeoutEmittedFalseAndPaidAtIsNullAndPaymentDeadlineLessThanEqual(
                AuctionStatus.ENDED.name(), now).stream().map(this::toDomain).toList();
    }

    private AuctionJpaEntity toEntity(Auction a) {
        return new AuctionJpaEntity(UUID.fromString(a.getId()), UUID.fromString(a.getProductId()), a.getSellerId(),
                a.getStartingPrice(), a.getBidIncrement(), a.getCurrentHighestBid(), a.getCurrentHighestBidderId(),
                a.getStatus().name(), a.getStartTime(), a.getEndTime(), a.getExtensionCount(), a.getWinnerId(),
                a.getFinalPrice(), a.getPaymentDeadline(), a.isPaymentTimeoutEmitted(), a.getPaidAt(),
                a.getCreatedAt(), a.getUpdatedAt());
    }

    private Auction toDomain(AuctionJpaEntity e) {
        return Auction.reconstitute(e.getId().toString(), e.getProductId().toString(), e.getSellerId(),
                e.getStartingPrice(), e.getBidIncrement(), e.getCurrentHighestBid(), e.getCurrentHighestBidderId(),
                AuctionStatus.valueOf(e.getStatus()), e.getStartTime(), e.getEndTime(), e.getExtensionCount(),
                e.getWinnerId(), e.getFinalPrice(), e.getPaymentDeadline(), e.isPaymentTimeoutEmitted(),
                e.getPaidAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
