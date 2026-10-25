package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ObservabilityConfigTest {

    private final ObservabilityConfig config = new ObservabilityConfig();
    private final ObservationPredicate predicate = config.noActuatorObservationPredicate();

    @Test
    @DisplayName("Should return false for /actuator endpoints to suppress tracing noise")
    void shouldReturnFalseForActuatorEndpoints() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServerRequestObservationContext context =
                new ServerRequestObservationContext(request, response);

        boolean result = predicate.test("http.server.requests", context);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return true for business API endpoints like /api/v1/**")
    void shouldReturnTrueForBusinessEndpoints() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/acme-corp/tickets");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServerRequestObservationContext context =
                new ServerRequestObservationContext(request, response);

        boolean result = predicate.test("http.server.requests", context);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return true for non-HTTP server observation contexts")
    void shouldReturnTrueForOtherContexts() {
        Observation.Context context = new Observation.Context();

        boolean result = predicate.test("custom.observation", context);

        assertThat(result).isTrue();
    }
}
