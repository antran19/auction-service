package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.infrastructure.persistence.entity.OutboxJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {
    List<OutboxJpaEntity> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

    // Only rows that were already published: an unpublished row is an event still waiting to go out.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OutboxJpaEntity o where o.publishedAt is not null and o.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
