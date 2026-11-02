package com.ribeiry.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KafkaPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private KafkaPublisher publisher;

    @Test
    void publish_deveChamarKafkaTemplateSendComParametrosCorretos() {
        publisher.publish("pedidos-eventos", "chave-123", "{\"id\":\"123\"}");

        verify(kafkaTemplate).send("pedidos-eventos", "chave-123", "{\"id\":\"123\"}");
    }

    @Test
    void publish_deveFuncionarComPayloadVazio() {
        publisher.publish("pedidos-eventos", "chave-456", "");

        verify(kafkaTemplate).send("pedidos-eventos", "chave-456", "");
    }
}
