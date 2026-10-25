package com.tlcn.discount_service.model;

import java.time.Instant;

import com.tlcn.discount_service.enums.DiscountType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Discount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;        // Mã giảm giá (nếu là voucher)
    private String description;

    @Enumerated(EnumType.STRING)
    private DiscountType type;  // PERCENTAGE (%), FIXED_AMOUNT (vnđ)
    private Double discountValue;

    private Double minOrderValue; // Giá trị đơn hàng tối thiểu
    private Double maxDiscount;   // Giảm tối đa bao nhiêu (nếu là %)

    private Instant startDate;
    private Instant endDate;
    private Integer usageLimit;   // Tổng số lần mã này được dùng
    private Integer usedCount;    // Đã dùng bao nhiêu lần
}