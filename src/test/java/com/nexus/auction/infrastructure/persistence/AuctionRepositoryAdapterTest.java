package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.domain.model.Auction;
import com.nexus.auction.domain.model.AuctionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.nexus.common.core.exception.ConflictException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuctionRepositoryAdapter.class)
class AuctionRepositoryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private AuctionRepositoryAdapter adapter;

    private Auction newAuction() {
        return Auction.create("11111111-1111-1111-1111-111111111111", "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
    }

    @Test
    void saveThenFindById_roundTripsTheAuction() {
        Auction auction = newAuction();

        adapter.save(auction);
        Optional<Auction> found = adapter.findById(auction.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(AuctionStatus.PENDING);
        assertThat(found.get().getStartingPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void findByIdForUpdate_returnsTheSameRowAsFindById() {
        Auction auction = newAuction();
        adapter.save(auction);

        Optional<Auction> found = adapter.findByIdForUpdate(auction.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(auction.getId());
    }

    @Test
    void existsActiveOrPendingForProduct_trueWhilePendingOrActive() {
        Auction auction = newAuction();
        adapter.save(auction);

        assertThat(adapter.existsActiveOrPendingForProduct(auction.getProductId())).isTrue();
    }

    @Test
    void existsActiveOrPendingForProduct_falseOnceEnded() {
        Auction auction = newAuction().withStatus(AuctionStatus.ENDED);
        adapter.save(auction);

        assertThat(adapter.existsActiveOrPendingForProduct(auction.getProductId())).isFalse();
    }

    @Test
    void countActiveOrPendingBySeller_countsOnlyThatSellersPendingAndActive() {
        adapter.save(newAuction());
        adapter.save(newAuction().withStatus(AuctionStatus.CANCELLED));

        assertThat(adapter.countActiveOrPendingBySeller("seller-1")).isEqualTo(1);
    }

    @Test
    void findById_returnsEmptyForMalformedId() {
        assertThat(adapter.findById("not-a-uuid")).isEmpty();
    }

    @Test
    void save_rejectsASecondPendingOrActiveAuctionForTheSameProduct() {
        // CreateAuctionUseCase already check-then-inserts (existsActiveOrPendingForProduct
        // then save), but that is a TOCTOU race under concurrent requests. The DB-level
        // partial unique index is the actual guarantee; this proves it exists and that the
        // adapter maps the resulting constraint violation to a domain ConflictException
        // rather than letting a raw DataIntegrityViolationException escape as a 500.
        Auction first = newAuction();
        adapter.save(first);
        Auction second = Auction.create(first.getProductId(), "seller-2",
                new BigDecimal("200.00"), new BigDecimal("20.00"),
                Instant.now(), Instant.now().plus(2, ChronoUnit.HOURS));

        assertThatThrownBy(() -> adapter.save(second)).isInstanceOf(ConflictException.class);
    }
}
