package com.ribeiry.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

    private KafkaConfig config;

    @BeforeEach
    void setup() {
        config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
    }

    @Test
    void producerFactory_deveRetornarDefaultKafkaProducerFactory() {
        ProducerFactory<String, String> factory = config.producerFactory();
        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
    }

    @Test
    void producerFactory_deveConterBootstrapServersNasConfiguracoes() {
        DefaultKafkaProducerFactory<String, String> factory =
                (DefaultKafkaProducerFactory<String, String>) config.producerFactory();
        assertThat(factory.getConfigurationProperties())
                .containsEntry("bootstrap.servers", "localhost:9092");
    }

    @Test
    void kafkaTemplate_deveRetornarNaoNulo() {
        KafkaTemplate<String, String> template = config.kafkaTemplate();
        assertThat(template).isNotNull();
    }
}
