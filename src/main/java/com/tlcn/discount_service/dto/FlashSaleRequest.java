package com.tlcn.discount_service.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleRequest {

    @NotBlank(message = "Flash sale name cannot be blank")
    private String name;

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Flash sale price is required")
    @Min(value = 0, message = "Flash sale price must be greater than or equal to 0")
    private Double flashSalePrice;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    private Instant startTime;

    @NotNull(message = "End time is required")
    @Future(message = "End time must be in the future")
    private Instant endTime;
    
    private String keycloakId;
}