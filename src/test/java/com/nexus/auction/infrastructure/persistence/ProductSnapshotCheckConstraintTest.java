package com.nexus.auction.infrastructure.persistence;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V9: the database itself refuses values the application already forbids.
// No surrounding transaction: each insert must commit (or fail) on its own.
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductSnapshotCheckConstraintTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;

    private void insertSnapshot(String status) {
        jdbcTemplate.update("INSERT INTO product_snapshots (product_id, seller_id, status, updated_at) "
                + "VALUES (?, 'seller-1', ?, now())", UUID.randomUUID().toString(), status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "ACTIVE", "INACTIVE", "SOLD"})
    void everyProductStatusCatalogCanSend_isAccepted(String status) {
        assertThatCode(() -> insertSnapshot(status)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "active", "DELETED", ""})
    void aStatusOutsideTheProductStatusEnum_isRejected(String status) {
        assertThatThrownBy(() -> insertSnapshot(status))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_product_snapshots_status");
    }
}
