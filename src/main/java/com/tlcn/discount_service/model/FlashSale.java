package com.tlcn.discount_service.model;

import java.time.Instant;

import com.tlcn.discount_service.enums.FlashSaleStatus;

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
public class FlashSale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId; 
    private String keycloakId;

    private String name;
    private Double flashSalePrice;
    private Instant startTime;
    private Instant endTime;
    
    @Enumerated(EnumType.STRING)
    private FlashSaleStatus status; // PENDING, ACTIVE, EXPIRED, CANCELLED

    private String quartzJobId; // Lưu ID của Quartz Job để quản lý nếu cần hủy/sửa
}