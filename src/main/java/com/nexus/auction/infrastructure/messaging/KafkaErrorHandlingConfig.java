package com.nexus.auction.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nexus.common.core.exception.DomainException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

// What happens when a @KafkaListener throws. Without this, Spring's default retries 9 times
// back-to-back and then just logs and skips the record -- the event is lost for good.
// Here: retry 3 times waiting 1s, 2s, 4s (enough for a DB/network blip), then copy the record
// to "<topic>.DLT" so it is never silently lost and can be inspected/replayed later, and move
// on so one bad record doesn't block the partition. Broken JSON or a business-rule rejection
// (DomainException) will fail the same way every time, so those skip the retries and go
// straight to the DLT. Spring Boot applies this bean to every listener automatically.
@Configuration
public class KafkaErrorHandlingConfig {

    static final int MAX_RETRIES = 3;
    static final long INITIAL_INTERVAL_MS = 1000;
    static final double MULTIPLIER = 2.0;
    static final String DLT_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate) {
        // KafkaTemplate is looked up on the first failure, not at startup: tests that exclude
        // KafkaAutoConfiguration have no KafkaTemplate bean and must still start. Partition -1 =
        // let Kafka pick, since the auto-created DLT may have fewer partitions than the source.
        ConsumerRecordRecoverer deadLetter = (record, ex) -> new DeadLetterPublishingRecoverer(
                kafkaTemplate.getObject(), (r, e) -> new TopicPartition(r.topic() + DLT_SUFFIX, -1))
                .accept(record, ex);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);

        DefaultErrorHandler handler = new DefaultErrorHandler(deadLetter, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, DomainException.class);
        return handler;
    }
}
