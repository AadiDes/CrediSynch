package com.credisynch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.credisynch.api.config.AppProperties;
import com.credisynch.api.decision.DecisionAction;
import com.credisynch.api.decision.GraphLinkageService;
import com.credisynch.api.persistence.ApplicationRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GraphLinkageServiceTest {

    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final AppProperties.Graph config = new AppProperties.Graph(720, 1, 3);

    private GraphLinkageService service() {
        AppProperties properties = new AppProperties(
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Ml("http://localhost:8000", 400),
                new AppProperties.Hashing("test-key"),
                new AppProperties.Policy("policy-test", 1500.0, 3.0, 0.8, 5.0, 0.1, 150.0),
                config,
                new AppProperties.Restricted(50000, 3, 90, 0.45),
                new AppProperties.Bedrock("apac.amazon.nova-lite-v1:0", "amazon.titan-embed-text-v2:0"));
        return new GraphLinkageService(applications, properties);
    }

    @Test
    @DisplayName("no shared identities means zero risk and no floor")
    void noSharingIsClean() {
        given(applications.countLinkedApplications(anyList(), anyInt())).willReturn(0);

        GraphLinkageService.Assessment assessment = service().assess(Arrays.asList("h1", "h2", null));

        assertThat(assessment.linkedApplicationCount()).isEqualTo(0);
        assertThat(assessment.risk()).isEqualTo(0.0);
        assertThat(assessment.floor()).isNull();
    }

    @Test
    @DisplayName("null hashes are filtered out before the repository is queried")
    void nullHashesAreFiltered() {
        given(applications.countLinkedApplications(anyList(), anyInt())).willReturn(0);

        service().assess(Arrays.asList("h1", null, "h2"));

        org.mockito.Mockito.verify(applications).countLinkedApplications(List.of("h1", "h2"), 720);
    }

    @Test
    @DisplayName("one shared identity floors the decision at STEP_UP, not higher")
    void oneLinkFloorsAtStepUp() {
        given(applications.countLinkedApplications(anyList(), anyInt())).willReturn(1);

        GraphLinkageService.Assessment assessment = service().assess(List.of("h1"));

        assertThat(assessment.floor()).isEqualTo(DecisionAction.STEP_UP);
        assertThat(assessment.risk()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("three or more shared applications looks like a ring: floor at REVIEW")
    void ringSizedSharingFloorsAtReview() {
        given(applications.countLinkedApplications(anyList(), anyInt())).willReturn(3);

        GraphLinkageService.Assessment assessment = service().assess(List.of("h1"));

        assertThat(assessment.floor()).isEqualTo(DecisionAction.REVIEW);
        assertThat(assessment.risk()).isCloseTo(0.75, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("risk climbs with diminishing returns and never reaches 1")
    void riskSaturatesButNeverReachesOne() {
        given(applications.countLinkedApplications(anyList(), anyInt())).willReturn(50);

        GraphLinkageService.Assessment assessment = service().assess(List.of("h1"));

        assertThat(assessment.risk()).isLessThan(1.0);
        assertThat(assessment.risk()).isGreaterThan(0.9);
    }
}
