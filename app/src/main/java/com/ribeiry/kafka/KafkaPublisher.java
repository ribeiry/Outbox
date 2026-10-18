package com.ribeiry.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaPublisher(KafkaTemplate<String,String> kafkaTemplate){
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String key, String payload) {
        kafkaTemplate.send(topic,key,payload);
        System.out.println("[KAFKA] Mensagem enviada. topic=" + topic + " key=" + key);
    }
}
