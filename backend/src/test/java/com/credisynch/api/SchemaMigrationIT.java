package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the real migrations against a real Postgres with pgvector (needs Docker; runs in `mvn verify`).
 * Proves the schema applies cleanly and that vector search is available, which is where
 * most "works on my machine" surprises come from.
 */
@SpringBootTest
@Testcontainers
class SchemaMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("credisynch")
            .withUsername("credisynch")
            .withPassword("testpassword");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Avoid contacting Keycloak during context startup.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9/jwks.json");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("every core table is created by flyway")
    void schemaIsCreated() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("applications", "decisions", "cases", "entity_links",
                "rings", "case_embeddings", "card_accounts", "transactions", "audit_log");
    }

    @Test
    @DisplayName("pgvector is installed and cosine distance works")
    void pgvectorIsUsable() {
        Double distance = jdbcTemplate.queryForObject(
                "SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector", Double.class);
        assertThat(distance).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("decisions only accept actions the policy engine can emit")
    void decisionActionsAreConstrained() {
        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'decisions'::regclass",
                String.class);
        assertThat(constraints).anyMatch(c -> c.contains("APPROVE_RESTRICTED"));
    }
}
