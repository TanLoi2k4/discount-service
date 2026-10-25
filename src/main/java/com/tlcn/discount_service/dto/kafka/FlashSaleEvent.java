package com.tlcn.discount_service.dto.kafka;

import lombok.*;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FlashSaleEvent {
    private Long flashSaleId;
    private Long productId;
    private Double salePrice;
    private Instant endTime;
    private String eventType; // "START", "END", "CANCELLED"
}