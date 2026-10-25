package com.tlcn.discount_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DiscountPreviewRequest {

    @NotBlank
    private String code;

    @NotNull
    @Min(0)
    private Double subtotal;
}
