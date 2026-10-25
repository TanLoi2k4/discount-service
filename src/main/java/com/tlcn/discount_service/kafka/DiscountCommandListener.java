package com.tlcn.discount_service.kafka;

import java.nio.charset.StandardCharsets;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.tlcn.discount_service.dto.kafka.DiscountCommand;
import com.tlcn.discount_service.dto.kafka.DiscountCommandType;
import com.tlcn.discount_service.dto.kafka.DiscountEvent;
import com.tlcn.discount_service.kafka.DiscountEventProducer;
import com.tlcn.discount_service.service.DiscountKafkaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountCommandListener {

    private final DiscountKafkaService discountKafkaService;
    private final DiscountEventProducer discountEventProducer;

    /**
     * Skeleton listener only (no business integration yet).
     * Later you will route commands to hold/commit/release handlers.
     */
    @KafkaListener(
            topics = "${spring.kafka.topics.discount-commands:discount.commands}",
            groupId = "${spring.kafka.consumer.group-id:discount-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onCommand(
            @Payload DiscountCommand command,
            @Header(name = "messageId", required = false) byte[] messageIdHeader,
            @Header(name = "replyTo", required = false) byte[] replyToHeader,
            Acknowledgment ack) {

        try {
            String messageId = messageIdHeader != null ? new String(messageIdHeader, StandardCharsets.UTF_8) : null;
            String replyTo = replyToHeader != null ? new String(replyToHeader, StandardCharsets.UTF_8) : null;
            log.info("Received discount command: messageId={}, type={}, commandId={}, orderId={}, code={}, userId={}",
                    messageId,
                    command != null ? command.getType() : null,
                    command != null ? command.getCommandId() : null,
                    command != null ? command.getOrderId() : null,
                    command != null ? command.getCode() : null,
                    command != null ? command.getUserId() : null);

            DiscountKafkaService.ProcessingResult result = discountKafkaService.handleCommand(command, messageId);

            if (result != null && result.event() != null) {
                DiscountEvent event = result.event();
                String correlationId = (messageId != null && !messageId.isBlank()) ? messageId : (command != null ? command.getCommandId() : null);

                boolean hasReplyTo = replyTo != null && !replyTo.isBlank();

                log.info("Discount command routing: messageId={} correlationId={} replyTo={} type={} orderId={}",
                    messageId,
                    correlationId,
                    replyTo,
                    command != null ? command.getType() : null,
                    command != null ? command.getOrderId() : null
                );

                // For pre-order HOLD (orderId is null): reply only, avoid publishing to discount.events
                boolean isHoldRequested = command != null && command.getType() == DiscountCommandType.HOLD_REQUESTED;
                boolean hasOrderId = command != null && command.getOrderId() != null;

                if (hasReplyTo) {
                    discountEventProducer.publishTo(replyTo, event, correlationId);
                    if (!isHoldRequested || hasOrderId) {
                        discountEventProducer.publishTo(null, event, correlationId);
                    }
                } else {
                    discountEventProducer.publish(event);
                }

                if (result.idempotencyId() != null && !result.idempotencyId().isBlank()) {
                    discountKafkaService.markProcessedAfterPublish(result.idempotencyId());
                }
            }

            ack.acknowledge();
        } catch (Exception ex) {
            String messageId = messageIdHeader != null ? new String(messageIdHeader, StandardCharsets.UTF_8) : null;
            log.error("Error handling discount command: messageId={}, err={}", messageId, ex.getMessage(), ex);
            // no ack => message will be retried (manual_immediate)
            throw ex;
        }
    }
}
