package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.application.port.out.ProductStockRepositoryPort;
import com.nexus.auction.infrastructure.persistence.entity.ProductStockLevelJpaEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class ProductStockRepositoryAdapter implements ProductStockRepositoryPort {

    private final ProductStockLevelJpaRepository jpaRepository;

    public ProductStockRepositoryAdapter(ProductStockLevelJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int availableQuantity(String productId) {
        return jpaRepository.findById(productId).map(ProductStockLevelJpaEntity::getAvailableQuantity).orElse(0);
    }

    @Override
    @Transactional
    public void applyIfNewer(String productId, int availableQuantity, Instant asOf) {
        jpaRepository.applyIfNewer(productId, availableQuantity, asOf);
    }
}
