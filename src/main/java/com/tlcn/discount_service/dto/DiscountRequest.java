package com.tlcn.discount_service.dto;

import java.time.Instant;

import com.tlcn.discount_service.enums.DiscountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class DiscountRequest {

    @NotBlank(message = "Discount code is required")
    private String code;

    private String description;

    @NotNull(message = "Discount type is required")
    private DiscountType type;

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be > 0")
    private Double discountValue;

    @PositiveOrZero(message = "Min order value must be >= 0")
    private Double minOrderValue;

    @PositiveOrZero(message = "Max discount must be >= 0")
    private Double maxDiscount;

    private Instant startDate;

    private Instant endDate;

    @PositiveOrZero(message = "Usage limit must be >= 0")
    private Integer usageLimit;
}
