package com.tlcn.discount_service.dto;

import com.tlcn.discount_service.enums.DiscountType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountPreviewResponse {

    private boolean valid;
    private String code;
    private Double subtotal;
    private Double discountAmount;
    private Double finalTotal;
    private DiscountType discountType;
    private String message;
}
