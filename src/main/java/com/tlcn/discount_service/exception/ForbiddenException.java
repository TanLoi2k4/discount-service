package com.tlcn.discount_service.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {
	public ForbiddenException(String message) {
		super(message, HttpStatus.FORBIDDEN);
	}

	public ForbiddenException(String message, String errorCode) {
		super(message, HttpStatus.FORBIDDEN, errorCode);
	}
}
