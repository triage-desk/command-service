package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config;

import io.micrometer.observation.ObservationPredicate;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
@NullMarked
public class ObservabilityConfig {

    @Bean
    public ObservationPredicate noActuatorObservationPredicate() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverContext) {
                String path = serverContext.getCarrier().getRequestURI();
                return path == null || !path.startsWith("/actuator");
            }
            return true;
        };
    }
}
