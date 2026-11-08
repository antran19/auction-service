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


import java.util.List;
import java.util.Map;

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
    void theNameRoundTrips_andARenameKeepsTheOwnerAndStatus() {
        adapter.insertIfAbsent(AuctionProduct.create("product-1", "seller-1", "Watch"));
        adapter.save(adapter.findById("product-1").orElseThrow().withStatus("ACTIVE"));
        adapter.save(adapter.findById("product-1").orElseThrow().withName("Vintage Watch"));
        entityManager.flush();
        entityManager.clear();

        AuctionProduct found = adapter.findById("product-1").orElseThrow();
        assertThat(found.getName()).isEqualTo("Vintage Watch");
        assertThat(found.getStatus()).isEqualTo("ACTIVE");
        assertThat(found.getSellerId()).isEqualTo("seller-1");
    }

    @Test
    void findNamesByIds_returnsOnlyKnownNames_leavingOutUnnamedAndUnknownProducts() {
        adapter.insertIfAbsent(AuctionProduct.create("named-1", "seller-1", "Watch"));
        adapter.insertIfAbsent(AuctionProduct.create("unnamed-1", "seller-1"));
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.findNamesByIds(List.of("named-1", "unnamed-1", "unknown-1")))
                .containsExactly(Map.entry("named-1", "Watch"));
        assertThat(adapter.findNamesByIds(List.of())).isEmpty();
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
