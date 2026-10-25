package com.tlcn.discount_service.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tlcn.discount_service.dto.DiscountPreviewResponse;
import com.tlcn.discount_service.model.Discount;
import com.tlcn.discount_service.repository.DiscountRepository;
import com.tlcn.discount_service.util.DiscountRules;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DiscountPreviewService {

    private final DiscountRepository discountRepository;

    @Transactional(readOnly = true)
    public DiscountPreviewResponse preview(String code, Double subtotal) {
        String normalizedCode = DiscountRules.normalizeCode(code);
        if (normalizedCode == null || normalizedCode.isBlank()) {
            return invalid(null, subtotal, "Missing discount code");
        }

        if (subtotal == null) {
            return invalid(normalizedCode, null, "Missing subtotal");
        }
        if (subtotal < 0) {
            return invalid(normalizedCode, subtotal, "Subtotal must be >= 0");
        }

        Instant now = Instant.now();
        Discount discount = discountRepository.findActiveByCode(normalizedCode, now).orElse(null);
        if (discount == null) {
            return invalid(normalizedCode, subtotal, "Discount is expired or not active");
        }

        if (discount.getMinOrderValue() != null && subtotal < discount.getMinOrderValue()) {
            return invalid(normalizedCode, subtotal, "Order subtotal is below minimum order value");
        }

        double discountAmount = DiscountRules.calculateDiscountAmount(discount, subtotal);
        double finalTotal = Math.max(0.0, subtotal - discountAmount);

        return DiscountPreviewResponse.builder()
            .valid(true)
            .code(normalizedCode)
            .subtotal(subtotal)
            .discountAmount(discountAmount)
            .finalTotal(finalTotal)
            .discountType(discount.getType())
            .message("Discount applied")
            .build();
    }

    private static DiscountPreviewResponse invalid(String code, Double subtotal, String message) {
        return DiscountPreviewResponse.builder()
            .valid(false)
            .code(code)
            .subtotal(subtotal)
            .message(message)
            .build();
    }
}
