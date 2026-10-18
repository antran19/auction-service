package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.domain.model.AuctionProduct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProductSnapshotRepositoryAdapter.class)
class ProductSnapshotRepositoryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private ProductSnapshotRepositoryAdapter adapter;
    @Autowired private TestEntityManager entityManager;

    @Test
    void insertIfAbsent_thenFindById_roundTrips() {
        adapter.insertIfAbsent(AuctionProduct.create("product-1", "seller-1"));
        entityManager.flush();
        entityManager.clear();

        AuctionProduct found = adapter.findById("product-1").orElseThrow();
        assertThat(found.getSellerId()).isEqualTo("seller-1");
        assertThat(found.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void insertIfAbsent_doesNotResetAnAlreadyActiveProduct() {
        adapter.insertIfAbsent(AuctionProduct.create("product-1", "seller-1"));
        adapter.save(adapter.findById("product-1").orElseThrow().withStatus("ACTIVE"));
        entityManager.flush();
        entityManager.clear();

        adapter.insertIfAbsent(AuctionProduct.create("product-1", "seller-1"));
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.findById("product-1").orElseThrow().getStatus()).isEqualTo("ACTIVE");
    }
}
