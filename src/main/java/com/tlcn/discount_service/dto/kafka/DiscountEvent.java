package com.tlcn.discount_service.dto.kafka;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountEvent {
    private String eventId;        // UUID
    private DiscountEventType type;

    private Long orderId;
    private String userId;
    private String code;

    private Long holdId;

    private Double discountAmount;
    private Double finalTotal;

    private Instant createdAt;
    private String message;        // human-readable info
}
