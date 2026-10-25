package com.tlcn.discount_service.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.tlcn.discount_service.dto.kafka.DiscountEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.discount-events:discount.events}")
    private String discountEventsTopic;

    public void publish(DiscountEvent event) {
        publishTo(discountEventsTopic, event, null);
    }

    public void publishTo(String topic, DiscountEvent event, String correlationId) {
        if (event == null) {
            return;
        }

        String effectiveTopic = (topic != null && !topic.isBlank()) ? topic : discountEventsTopic;

        String messageId = event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString();
        long timestampMs = Instant.now().toEpochMilli();

        String key = event.getOrderId() != null ? String.valueOf(event.getOrderId()) : null;

        ProducerRecord<String, Object> record = new ProducerRecord<>(effectiveTopic, key, event);
        record.headers().add("messageId", messageId.getBytes(StandardCharsets.UTF_8));
        // Keep idempotency-key aligned with messageId for deterministic deduplication on consumers.
        record.headers().add("idempotency-key", messageId.getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", String.valueOf(timestampMs).getBytes(StandardCharsets.UTF_8));

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(record);
        log.info("Published discount event: messageId={}, correlationId={}, type={}, orderId={} -> topic={}",
                messageId, correlationId, event.getType(), event.getOrderId(), effectiveTopic);
    }
}
