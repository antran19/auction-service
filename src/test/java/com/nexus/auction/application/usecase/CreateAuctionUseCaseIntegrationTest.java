package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.auction.application.port.out.EventPublisherPort;
import com.nexus.auction.infrastructure.persistence.OutboxJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CreateAuctionUseCaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired private CreateAuctionUseCase createAuctionUseCase;
    @Autowired private AuctionRepositoryPort auctionRepositoryPort;
    @Autowired private OutboxJpaRepository outboxJpaRepository;

    private CreateAuctionCommand command() {
        // Random productId each call — the outer test and the nested test share the same
        // Spring context/database with no rollback between them, and CreateAuctionUseCase
        // correctly rejects a second active/pending auction on the same product. A fixed
        // literal here made the nested test collide with the outer test's already-persisted
        // auction and fail with the wrong exception (ConflictException, not the simulated
        // outbox failure).
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        return new CreateAuctionCommand(UUID.randomUUID().toString(), "seller-1",
                new BigDecimal("100.00"), new BigDecimal("10.00"), start, start.plus(2, ChronoUnit.HOURS));
    }

    @Test
    void create_persistsAuctionAndAnUnpublishedOutboxRow() {
        AuctionResult result = createAuctionUseCase.create(command());

        assertThat(auctionRepositoryPort.findById(result.id())).isPresent();
        assertThat(outboxJpaRepository.findAll())
                .anySatisfy(row -> {
                    assertThat(row.getAggregateId()).isEqualTo(result.id());
                    assertThat(row.getEventType()).isEqualTo("AuctionCreated");
                    assertThat(row.getPublishedAt()).isNull();
                });
    }

    @Nested
    @Import(WhenOutboxWriteFails.FailingEventPublisherConfig.class)
    class WhenOutboxWriteFails {

        @Autowired private CreateAuctionUseCase createAuctionUseCase;
        @Autowired private JdbcTemplate jdbcTemplate;

        @Test
        void create_rollsBackTheAuctionInsertWhenTheOutboxWriteThrows() {
            long before = count();

            assertThatThrownBy(() -> createAuctionUseCase.create(command()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("simulated outbox failure");

            assertThat(count()).isEqualTo(before);
        }

        private long count() {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auctions", Long.class);
        }

        @TestConfiguration
        static class FailingEventPublisherConfig {
            @Bean
            @Primary
            EventPublisherPort failingEventPublisherPort() {
                return event -> {
                    throw new RuntimeException("simulated outbox failure");
                };
            }
        }
    }
}
