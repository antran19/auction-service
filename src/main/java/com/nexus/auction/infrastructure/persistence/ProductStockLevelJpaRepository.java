package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.infrastructure.persistence.entity.ProductStockLevelJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProductStockLevelJpaRepository extends JpaRepository<ProductStockLevelJpaEntity, String> {

    // One statement: the row only changes when the incoming level was reported at the same time
    // or later than the stored one, so an older redelivered event cannot roll it back.
    @Modifying
    @Query(value = "INSERT INTO product_stock_levels (product_id, available_quantity, as_of) "
            + "VALUES (:productId, :available, :asOf) "
            + "ON CONFLICT (product_id) DO UPDATE SET available_quantity = EXCLUDED.available_quantity, "
            + "as_of = EXCLUDED.as_of WHERE product_stock_levels.as_of <= EXCLUDED.as_of", nativeQuery = true)
    void applyIfNewer(@Param("productId") String productId, @Param("available") int available,
                      @Param("asOf") Instant asOf);
}
