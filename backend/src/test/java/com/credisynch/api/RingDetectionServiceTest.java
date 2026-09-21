package com.credisynch.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.decision.RingDetectionClient;
import com.credisynch.api.decision.RingDetectionClient.RingClusterBody;
import com.credisynch.api.decision.RingDetectionClient.RingDetectionResponseBody;
import com.credisynch.api.decision.RingDetectionService;
import com.credisynch.api.persistence.ApplicationRepository;
import com.credisynch.api.persistence.GraphRecords.EntityLinkRow;
import com.credisynch.api.persistence.RingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RingDetectionServiceTest {

    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final RingRepository rings = mock(RingRepository.class);
    private final RingDetectionClient client = mock(RingDetectionClient.class);

    private RingDetectionService service() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                new AppProperties.Graph(720, 1, 3),
                new AppProperties.Restricted(50000, 3, 90, 0.45, 0.75),
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"),
                new AppProperties.Llm("bedrock", null, "gemini-2.5-flash", "gemini-embedding-001"));
        return new RingDetectionService(applications, rings, client, properties);
    }

    @Test
    @DisplayName("a candidate cluster below the ring-size threshold never calls the graph service")
    void belowThresholdSkipsDetection() {
        UUID applicationId = UUID.randomUUID();
        given(applications.findConnectedApplicationIds(applicationId))
                .willReturn(List.of(applicationId, UUID.randomUUID(), UUID.randomUUID()));

        service().detectAsync(applicationId);

        verify(client, never()).detect(any(), anyInt());
    }

    @Test
    @DisplayName("a ring-sized cluster with no existing ring is inserted, and every member persisted")
    void newRingIsCreated() {
        UUID applicationId = UUID.randomUUID();
        List<UUID> cluster = List.of(applicationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(applications.findConnectedApplicationIds(applicationId)).willReturn(cluster);
        given(applications.findEntityLinks(cluster)).willReturn(
                List.of(new EntityLinkRow(applicationId, "DEVICE", "shared-hash")));
        RingClusterBody clusterBody = new RingClusterBody(
                cluster.stream().map(UUID::toString).toList(), 4, 1.0);
        given(client.detect(any(), eq(4)))
                .willReturn(Optional.of(new RingDetectionResponseBody(List.of(clusterBody), "CONNECTED_COMPONENTS")));
        given(rings.findExistingRingId(any())).willReturn(Optional.empty());

        service().detectAsync(applicationId);

        verify(rings, times(1)).insertRing(any(), eq("CONNECTED_COMPONENTS"), eq(4), eq(1.0), any());
        verify(rings, never()).refreshRing(any(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), any());
        verify(rings, times(1)).addMembers(any(), eq(cluster));
    }

    @Test
    @DisplayName("a cluster matching an already-detected ring grows it instead of duplicating")
    void existingRingIsRefreshed() {
        UUID applicationId = UUID.randomUUID();
        UUID existingRingId = UUID.randomUUID();
        List<UUID> cluster = List.of(applicationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(applications.findConnectedApplicationIds(applicationId)).willReturn(cluster);
        given(applications.findEntityLinks(cluster)).willReturn(List.of());
        RingClusterBody clusterBody = new RingClusterBody(
                cluster.stream().map(UUID::toString).toList(), 4, 0.83);
        given(client.detect(any(), eq(4)))
                .willReturn(Optional.of(new RingDetectionResponseBody(List.of(clusterBody), "CONNECTED_COMPONENTS")));
        given(rings.findExistingRingId(any())).willReturn(Optional.of(existingRingId));

        service().detectAsync(applicationId);

        verify(rings, never()).insertRing(any(), any(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), any());
        verify(rings, times(1)).refreshRing(eq(existingRingId), eq(4), eq(0.83), any());
        verify(rings, times(1)).addMembers(eq(existingRingId), eq(cluster));
    }

    @Test
    @DisplayName("the graph service being unavailable never throws - just leaves the cluster undetected")
    void gracefulDegradationWhenGraphServiceFails() {
        UUID applicationId = UUID.randomUUID();
        List<UUID> cluster = List.of(applicationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        given(applications.findConnectedApplicationIds(applicationId)).willReturn(cluster);
        given(applications.findEntityLinks(cluster)).willReturn(List.of());
        given(client.detect(any(), eq(4))).willReturn(Optional.empty());

        service().detectAsync(applicationId);

        verify(rings, never()).insertRing(any(), any(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), any());
        verify(rings, never()).refreshRing(any(), anyInt(), org.mockito.ArgumentMatchers.anyDouble(), any());
    }
}
