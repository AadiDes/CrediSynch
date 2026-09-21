package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.credisynch.api.persistence.ApplicationRepository;
import com.credisynch.api.persistence.DecisionRecords.ApplicationRow;
import com.credisynch.api.persistence.GraphRecords.EntityLinkRow;
import com.credisynch.api.persistence.RingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the ring-detection SQL (the recursive connected-components query and ring persistence)
 * against a real Postgres - the recursive CTE syntax and the ON CONFLICT DO NOTHING member insert
 * are exactly the kind of thing that silently misbehaves under a mocked JdbcTemplate.
 */
@SpringBootTest
@Testcontainers
class RingDetectionIT {

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
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9/jwks.json");
    }

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private RingRepository rings;

    private UUID newApplication(String externalRef) {
        UUID id = UUID.randomUUID();
        applications.insert(new ApplicationRow(id, externalRef, "WEB", "partner-1",
                "namehash", "{}", "{}"));
        return id;
    }

    @Test
    @DisplayName("a shared device links two applications into one connected component")
    void directSharingIsConnected() {
        UUID a = newApplication("ring-it-a");
        UUID b = newApplication("ring-it-b");
        UUID c = newApplication("ring-it-c");
        applications.insertEntityLink(a, "DEVICE", "shared-device-1");
        applications.insertEntityLink(b, "DEVICE", "shared-device-1");
        applications.insertEntityLink(c, "DEVICE", "unrelated-device");

        List<UUID> cluster = applications.findConnectedApplicationIds(a);

        assertThat(cluster).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("connectivity transits through an intermediate application across different entity types")
    void transitiveChainIsConnected() {
        UUID a = newApplication("ring-it-chain-a");
        UUID b = newApplication("ring-it-chain-b");
        UUID c = newApplication("ring-it-chain-c");
        applications.insertEntityLink(a, "DEVICE", "chain-device");
        applications.insertEntityLink(b, "DEVICE", "chain-device");
        applications.insertEntityLink(b, "PHONE", "chain-phone");
        applications.insertEntityLink(c, "PHONE", "chain-phone");

        List<UUID> cluster = applications.findConnectedApplicationIds(a);

        assertThat(cluster).containsExactlyInAnyOrder(a, b, c);
    }

    @Test
    @DisplayName("an application with no shared identity is its own component of one")
    void isolatedApplicationIsAloneInItsComponent() {
        UUID a = newApplication("ring-it-isolated");

        List<UUID> cluster = applications.findConnectedApplicationIds(a);

        assertThat(cluster).containsExactly(a);
    }

    @Test
    @DisplayName("findEntityLinks returns exactly the rows for the requested applications")
    void entityLinksAreScoped() {
        UUID a = newApplication("ring-it-links-a");
        UUID b = newApplication("ring-it-links-b");
        UUID other = newApplication("ring-it-links-other");
        applications.insertEntityLink(a, "DEVICE", "d1");
        applications.insertEntityLink(b, "EMAIL", "e1");
        applications.insertEntityLink(other, "DEVICE", "d-other");

        List<EntityLinkRow> links = applications.findEntityLinks(List.of(a, b));

        assertThat(links).extracting(EntityLinkRow::applicationId).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("a ring persists, is found by any member, and grows without duplicating membership")
    void ringPersistsAndGrows() {
        UUID a = newApplication("ring-it-persist-a");
        UUID b = newApplication("ring-it-persist-b");
        UUID c = newApplication("ring-it-persist-c");
        UUID ringId = UUID.randomUUID();

        rings.insertRing(ringId, "CONNECTED_COMPONENTS", 2, 1.0, "2 applications linked");
        rings.addMembers(ringId, List.of(a, b));

        assertThat(rings.findExistingRingId(List.of(b))).contains(ringId);
        assertThat(rings.findExistingRingId(List.of(c))).isEmpty();

        rings.refreshRing(ringId, 3, 0.67, "3 applications linked");
        rings.addMembers(ringId, List.of(a, b, c));

        assertThat(rings.findExistingRingId(List.of(c))).contains(ringId);
        Optional<UUID> stillTheSameRing = rings.findExistingRingId(List.of(a));
        assertThat(stillTheSameRing).contains(ringId);
    }
}
