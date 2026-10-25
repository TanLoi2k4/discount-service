package com.tlcn.discount_service.dto;

import lombok.Data;

@Data
public class ProductSummary {
    private Long id;
    private Double price;
    private String keycloakId;
    private boolean deleted;
}
