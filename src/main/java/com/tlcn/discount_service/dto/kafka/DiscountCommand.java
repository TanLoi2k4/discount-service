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
public class DiscountCommand {
    private String commandId;      // UUID for idempotency
    private DiscountCommandType type;

    private Long orderId;
    private String userId;
    private String code;

    private Long holdId;           // present for commit/release (and optionally in hold retry)

    private Double subtotal;
    private Instant createdAt;

    private String reason;         // optional (e.g., for release reason)
}
