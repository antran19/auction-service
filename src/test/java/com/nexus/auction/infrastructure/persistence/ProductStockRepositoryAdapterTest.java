package com.nexus.auction.infrastructure.persistence;

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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProductStockRepositoryAdapter.class)
class ProductStockRepositoryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private ProductStockRepositoryAdapter adapter;
    @Autowired private TestEntityManager entityManager;

    @Test
    void unknownProduct_hasZeroAvailable() {
        assertThat(adapter.availableQuantity("nope")).isZero();
    }

    @Test
    void applyIfNewer_keepsTheNewestLevel_evenWhenAnOlderEventArrivesLast() {
        adapter.applyIfNewer("product-1", 0, Instant.parse("2026-10-17T05:00:05Z"));
        adapter.applyIfNewer("product-1", 1, Instant.parse("2026-10-17T05:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.availableQuantity("product-1")).isZero();
    }
}
