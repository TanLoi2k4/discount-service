package com.tlcn.discount_service.kafka;

import com.tlcn.discount_service.dto.kafka.FlashSaleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.flash-sale:flash-sale}")
    private String flashSaleTopic;

    public void sendFlashSaleEvent(FlashSaleEvent event) {
        if (event == null) {
            return;
        }

        String messageId = UUID.randomUUID().toString();
        long timestampMs = Instant.now().toEpochMilli();

        String key = event.getProductId() != null ? String.valueOf(event.getProductId()) : null;

        ProducerRecord<String, Object> record = new ProducerRecord<>(flashSaleTopic, key, event);
        record.headers().add("messageId", messageId.getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", String.valueOf(timestampMs).getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record);
        log.info("Sent flash-sale event: messageId={}, eventType={}, flashSaleId={}, productId={} -> topic={}",
            messageId,
            event.getEventType(),
            event.getFlashSaleId(),
            event.getProductId(),
            flashSaleTopic
        );
    }
}
