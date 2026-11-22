package com.chubb.claims;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared setup for tests that go through HTTP, Spring, Flyway and a real Postgres (started in Docker by Testcontainers).
 * The demo seed data (local profile) is loaded, and each test runs in a transaction that is rolled back afterwards,
 * so tests don't affect each other.
 *
 * One container for the whole test run ("singleton container"): started once here and removed by Testcontainers
 * when the JVM exits. A per-class container would be stopped after the first class while Spring keeps reusing
 * its cached context, which still points at the stopped container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTestBase {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    /** Same Kafka image as docker-compose.yml. The app's consumers and outbox relay connect to it. */
    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    // Seeded users (see db/seed/V100__seed_demo_data.sql)
    protected static final String ALICE = "00000000-0000-0000-0000-000000000001";
    protected static final String BEN = "00000000-0000-0000-0000-000000000002";
    protected static final String PRIYA_STAFF = "00000000-0000-0000-0000-000000000011";
    protected static final String DANIEL_STAFF = "00000000-0000-0000-0000-000000000012";

    @Autowired
    protected MockMvc mvc;
}
