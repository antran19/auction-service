package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.infrastructure.persistence.entity.AuctionJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionJpaRepository extends JpaRepository<AuctionJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AuctionJpaEntity a WHERE a.id = :id")
    Optional<AuctionJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByProductIdAndStatusIn(UUID productId, List<String> statuses);

    long countBySellerIdAndStatusIn(String sellerId, List<String> statuses);

    @Query(value = """
            SELECT * FROM auctions a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:sellerId IS NULL OR a.seller_id = :sellerId)
              AND (:productId IS NULL OR a.product_id = CAST(:productId AS uuid))
            ORDER BY a.created_at DESC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<AuctionJpaEntity> search(
            @Param("status") String status,
            @Param("sellerId") String sellerId,
            @Param("productId") String productId,
            @Param("size") int size,
            @Param("offset") int offset);

    List<AuctionJpaEntity> findByStatusAndStartTimeLessThanEqual(String status, Instant now);

    List<AuctionJpaEntity> findByStatusAndEndTimeLessThanEqual(String status, Instant now);

    // PaidAtIsNull: a winner who already paid within the deadline must never surface here, even
    // if the deadline has since passed -- otherwise a fully-paid auction gets flagged as a
    // payment timeout.
    List<AuctionJpaEntity> findByStatusAndWinnerIdIsNotNullAndPaymentTimeoutEmittedFalseAndPaidAtIsNullAndPaymentDeadlineLessThanEqual(
            String status, Instant now);
}
