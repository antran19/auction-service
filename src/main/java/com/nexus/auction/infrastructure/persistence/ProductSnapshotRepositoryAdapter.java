package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.application.port.out.ProductSnapshotRepositoryPort;
import com.nexus.auction.domain.model.AuctionProduct;
import com.nexus.auction.infrastructure.persistence.entity.ProductSnapshotJpaEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
public class ProductSnapshotRepositoryAdapter implements ProductSnapshotRepositoryPort {

    private final ProductSnapshotJpaRepository jpaRepository;

    public ProductSnapshotRepositoryAdapter(ProductSnapshotJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<AuctionProduct> findById(String productId) {
        return jpaRepository.findById(productId)
                .map(e -> AuctionProduct.reconstitute(e.getProductId(), e.getSellerId(), e.getStatus()));
    }

    @Override
    @Transactional
    public void insertIfAbsent(AuctionProduct product) {
        jpaRepository.insertIfAbsent(product.getProductId(), product.getSellerId(), product.getStatus(), Instant.now());
    }

    @Override
    public void save(AuctionProduct product) {
        jpaRepository.save(new ProductSnapshotJpaEntity(product.getProductId(), product.getSellerId(),
                product.getStatus(), Instant.now()));
    }
}
