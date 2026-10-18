package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.infrastructure.persistence.entity.ProductSnapshotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProductSnapshotJpaRepository extends JpaRepository<ProductSnapshotJpaEntity, String> {

    @Modifying
    @Query(value = "INSERT INTO product_snapshots (product_id, seller_id, status, updated_at) "
            + "VALUES (:productId, :sellerId, :status, :updatedAt) "
            + "ON CONFLICT (product_id) DO NOTHING", nativeQuery = true)
    void insertIfAbsent(@Param("productId") String productId, @Param("sellerId") String sellerId,
                        @Param("status") String status, @Param("updatedAt") Instant updatedAt);
}
