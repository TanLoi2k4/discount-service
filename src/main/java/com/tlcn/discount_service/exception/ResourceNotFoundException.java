package com.tlcn.discount_service.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {
	public ResourceNotFoundException(String message) {
		super(message, HttpStatus.NOT_FOUND);
	}

	public ResourceNotFoundException(String message, String errorCode) {
		super(message, HttpStatus.NOT_FOUND, errorCode);
	}
}
