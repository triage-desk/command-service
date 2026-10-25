package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@NullMarked
public class KafkaTopicConfig {

    @Bean
    public NewTopic triageTicketsTopic(OutboxProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(3).replicas(1).build();
    }
}
