package com.chubb.claims.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

/** Kafka topics and consumer error handling. Topics are created at startup if they don't exist. */
@Configuration
@EnableScheduling
public class KafkaTopics {

    /** All claim events. Keyed by claim id, so each claim's events stay in order within a partition. */
    public static final String CLAIM_EVENTS = "claim-events";

    /** Events a consumer failed to process after retries. Same partition count, so failed records keep their partition. */
    public static final String CLAIM_EVENTS_DLT = "claim-events.DLT";

    private static final int PARTITIONS = 3;

    @Bean
    NewTopic claimEventsTopic() {
        return TopicBuilder.name(CLAIM_EVENTS).partitions(PARTITIONS).replicas(1).build();
    }

    @Bean
    NewTopic claimEventsDeadLetterTopic() {
        return TopicBuilder.name(CLAIM_EVENTS_DLT).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * For every consumer: retry a failing event 3 times, 1 second apart, then move it to the dead-letter topic,
     * so one bad event can't block the events behind it. Spring Boot applies this to all listeners.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), new FixedBackOff(1000L, 3L));
    }
}
