package com.tlcn.discount_service.util;

import java.time.Instant;

import com.tlcn.discount_service.enums.DiscountType;
import com.tlcn.discount_service.model.Discount;

public final class DiscountRules {

    private DiscountRules() {}

    public static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    public static boolean isActive(Discount discount, Instant now) {
        if (discount.getStartDate() != null && discount.getStartDate().isAfter(now)) {
            return false;
        }
        if (discount.getEndDate() != null && discount.getEndDate().isBefore(now)) {
            return false;
        }
        return true;
    }

    public static double calculateDiscountAmount(Discount discount, double subtotal) {
        if (discount.getDiscountValue() == null) {
            return 0.0;
        }

        double amount;
        if (discount.getType() == DiscountType.PERCENTAGE) {
            amount = subtotal * (discount.getDiscountValue() / 100.0);
            if (discount.getMaxDiscount() != null) {
                amount = Math.min(amount, discount.getMaxDiscount());
            }
        } else {
            amount = discount.getDiscountValue();
        }

        amount = Math.max(0.0, amount);
        return Math.min(amount, subtotal);
    }
}
