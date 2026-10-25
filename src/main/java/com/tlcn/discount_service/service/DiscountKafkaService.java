package com.tlcn.discount_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tlcn.discount_service.dto.kafka.DiscountCommand;
import com.tlcn.discount_service.dto.kafka.DiscountCommandType;
import com.tlcn.discount_service.dto.kafka.DiscountEvent;
import com.tlcn.discount_service.dto.kafka.DiscountEventType;
import com.tlcn.discount_service.exception.BusinessException;
import com.tlcn.discount_service.util.DiscountRules;
import com.tlcn.discount_service.kafka.DiscountEventProducer;
import com.tlcn.discount_service.model.Discount;
import com.tlcn.discount_service.repository.DiscountRepository;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountKafkaService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "discount:kafka:processed:";
    private static final String HOLD_KEY_PREFIX = "discount:hold:";

    private final DiscountRepository discountRepository;
    private final DiscountEventProducer discountEventProducer;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.discount.hold-ttl-minutes:15}")
    private long holdTtlMinutes;

    @Value("${app.discount.kafka.idempotency-ttl-hours:24}")
    private long idempotencyTtlHours;

    /**
     * Handle a DiscountCommand (Kafka consumer side).
     * Idempotency: use messageId header first, fallback to commandId.
     *
     * Note: idempotency is marked ONLY after publish succeeds (done by caller).
     */
    public ProcessingResult handleCommand(DiscountCommand command, String messageId) {
        if (command == null) {
            log.warn("Ignore null discount command (messageId={})", messageId);
            return null;
        }

        String idempotencyId = resolveIdempotencyId(command, messageId);
        if (idempotencyId != null && isAlreadyProcessed(idempotencyId)) {
            log.info("Skip already processed discount command: messageId={} commandId={}", messageId, command.getCommandId());
            return null;
        }

        DiscountEvent event = process(command);

        return new ProcessingResult(event, idempotencyId);
    }

    public void markProcessedAfterPublish(String idempotencyId) {
        if (idempotencyId == null || idempotencyId.isBlank()) {
            return;
        }
        markProcessed(idempotencyId);
    }

    public record ProcessingResult(DiscountEvent event, String idempotencyId) {}

    private DiscountEvent process(DiscountCommand command) {
        DiscountCommandType type = command.getType();
        if (type == null) {
            return buildEvent(command, DiscountEventType.HOLD_FAILED, "Missing command type", null, null);
        }

        return switch (type) {
            case HOLD_REQUESTED -> handleHold(command);
            case COMMIT_REQUESTED -> handleCommit(command);
            case RELEASE_REQUESTED -> handleRelease(command);
        };
    }

    private DiscountEvent handleHold(DiscountCommand command) {
        Instant now = Instant.now();

        String normalizedCode = DiscountRules.normalizeCode(command.getCode());
        if (normalizedCode == null || normalizedCode.isBlank()) {
            return buildEvent(command, DiscountEventType.HOLD_FAILED, "Missing discount code", null, null);
        }

        if (command.getSubtotal() == null) {
            return buildEvent(command, DiscountEventType.HOLD_FAILED, "Missing subtotal", null, null);
        }
        if (command.getSubtotal() < 0) {
            return buildEvent(command, DiscountEventType.HOLD_FAILED, "Subtotal must be >= 0", null, null);
        }

        Discount discount;
        try {
            discount = discountRepository.findActiveByCode(normalizedCode, now)
                .orElseThrow(() -> new BusinessException("Discount is expired or not active", HttpStatus.BAD_REQUEST));
        } catch (Exception ex) {
            return buildEvent(command, DiscountEventType.HOLD_FAILED, ex.getMessage(), null, null);
        }

        if (discount.getMinOrderValue() != null && command.getSubtotal() < discount.getMinOrderValue()) {
            return buildEvent(command, DiscountEventType.HOLD_FAILED,
                "Order subtotal is below minimum order value", null, null);
        }

        double discountAmount = DiscountRules.calculateDiscountAmount(discount, command.getSubtotal());
        double finalTotal = Math.max(0.0, command.getSubtotal() - discountAmount);

        long holdId = command.getHoldId() != null ? command.getHoldId() : generateHoldId();

        HoldData hold = HoldData.builder()
            .holdId(holdId)
            .commandId(command.getCommandId())
            .orderId(command.getOrderId())
            .userId(command.getUserId())
            .code(normalizedCode)
            .subtotal(command.getSubtotal())
            .discountAmount(discountAmount)
            .finalTotal(finalTotal)
            .createdAt(now)
            .build();

        saveHold(hold);

        return buildEvent(command, DiscountEventType.HELD, "Discount held", holdId, hold);
    }

    @Transactional
    private DiscountEvent handleCommit(DiscountCommand command) {
        if (command.getHoldId() == null) {
            return buildEvent(command, DiscountEventType.COMMIT_FAILED, "Missing holdId for commit", null, null);
        }

        HoldData hold = loadHold(command.getHoldId());
        if (hold == null) {
            return buildEvent(command, DiscountEventType.COMMIT_FAILED, "Hold not found or expired", command.getHoldId(), null);
        }

        Instant now = Instant.now();
        String normalizedCode = DiscountRules.normalizeCode(hold.getCode());
        if (normalizedCode == null || normalizedCode.isBlank()) {
            return buildEvent(command, DiscountEventType.COMMIT_FAILED, "Invalid discount code in hold", command.getHoldId(), hold);
        }

        Discount discount = discountRepository.findByCodeIgnoreCaseForUpdate(normalizedCode).orElse(null);
        if (discount == null) {
            return buildEvent(command, DiscountEventType.COMMIT_FAILED, "Discount not found", command.getHoldId(), hold);
        }

        // Re-check business rules at commit time.
        if (!DiscountRules.isActive(discount, now)) {
            return buildEvent(command, DiscountEventType.COMMIT_FAILED, "Discount is expired or not active", command.getHoldId(), hold);
        }

        if (discount.getUsageLimit() != null) {
            int used = discount.getUsedCount() != null ? discount.getUsedCount() : 0;
            if (used >= discount.getUsageLimit()) {
                return buildEvent(command, DiscountEventType.COMMIT_FAILED, "Discount usage limit reached", command.getHoldId(), hold);
            }
        }

        int currentUsed = discount.getUsedCount() != null ? discount.getUsedCount() : 0;
        discount.setUsedCount(currentUsed + 1);
        discountRepository.save(discount);

        deleteHold(command.getHoldId());

        return buildEvent(command, DiscountEventType.COMMITTED, "Discount committed", command.getHoldId(), hold);
    }

    private DiscountEvent handleRelease(DiscountCommand command) {
        if (command.getHoldId() == null) {
            return buildEvent(command, DiscountEventType.RELEASE_FAILED, "Missing holdId for release", null, null);
        }

        HoldData hold = loadHold(command.getHoldId());
        deleteHold(command.getHoldId());

        return buildEvent(
            command,
            DiscountEventType.RELEASED,
            hold != null ? "Discount released" : "Hold not found (treated as released)",
            command.getHoldId(),
            hold
        );
    }

    private DiscountEvent buildEvent(DiscountCommand command, DiscountEventType type, String message, Long holdId, HoldData hold) {
        Instant now = Instant.now();
        Double discountAmount = hold != null ? hold.getDiscountAmount() : null;
        Double finalTotal = hold != null ? hold.getFinalTotal() : null;
        String code = hold != null ? hold.getCode() : DiscountRules.normalizeCode(command.getCode());

        return DiscountEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .type(type)
            .orderId(command.getOrderId() != null ? command.getOrderId() : (hold != null ? hold.getOrderId() : null))
            .userId(command.getUserId() != null ? command.getUserId() : (hold != null ? hold.getUserId() : null))
            .code(code)
            .holdId(holdId)
            .discountAmount(discountAmount)
            .finalTotal(finalTotal)
            .createdAt(now)
            .message(message)
            .build();
    }

    private static long generateHoldId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }

    private String resolveIdempotencyId(DiscountCommand command, String messageId) {
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        if (command.getCommandId() != null && !command.getCommandId().isBlank()) {
            return command.getCommandId();
        }
        return null;
    }

    private boolean isAlreadyProcessed(String idempotencyId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(IDEMPOTENCY_KEY_PREFIX + idempotencyId));
    }

    private void markProcessed(String idempotencyId) {
        redisTemplate.opsForValue().set(
            IDEMPOTENCY_KEY_PREFIX + idempotencyId,
            "1",
            Duration.ofHours(idempotencyTtlHours)
        );
    }

    private void saveHold(HoldData hold) {
        String key = HOLD_KEY_PREFIX + hold.getHoldId();
        try {
            String json = objectMapper.writeValueAsString(hold);
            redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(holdTtlMinutes));
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize hold", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private HoldData loadHold(Long holdId) {
        if (holdId == null) {
            return null;
        }
        String key = HOLD_KEY_PREFIX + holdId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, HoldData.class);
        } catch (Exception ex) {
            log.warn("Failed to deserialize hold: holdId={} err={}", holdId, ex.getMessage());
            return null;
        }
    }

    private void deleteHold(Long holdId) {
        if (holdId == null) {
            return;
        }
        redisTemplate.delete(HOLD_KEY_PREFIX + holdId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class HoldData {
        private Long holdId;
        private String commandId;
        private Long orderId;
        private String userId;
        private String code;
        private Double subtotal;
        private Double discountAmount;
        private Double finalTotal;
        private Instant createdAt;
    }
}
