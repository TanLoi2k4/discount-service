package com.tlcn.discount_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResponseDTO<T> {
    private boolean success;
    private String message;
    private T data;
}