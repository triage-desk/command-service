package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@NullMarked
class KafkaTopicConfigTest {

    @Test
    @DisplayName("should configure topic with 3 partitions and 1 replica")
    void shouldConfigureTriageTicketsTopic() {
        KafkaTopicConfig config = new KafkaTopicConfig();
        OutboxProperties properties =
                new OutboxProperties("triage-desk.command.tickets", 50, 500, 5);

        NewTopic topic = config.triageTicketsTopic(properties);

        assertThat(topic.name()).isEqualTo("triage-desk.command.tickets");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}
