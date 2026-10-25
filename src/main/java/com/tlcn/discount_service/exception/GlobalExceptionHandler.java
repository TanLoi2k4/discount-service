package com.tlcn.discount_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.tlcn.discount_service.dto.ResponseDTO;

import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ResponseDTO<Void>> handleBusiness(BusinessException ex) {
		log.error("BusinessException: {}", ex.getMessage());
		HttpStatus status = ex.getStatus() != null ? ex.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(status).body(new ResponseDTO<>(false, ex.getMessage(), null));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ResponseDTO<Void>> handleNotFound(ResourceNotFoundException ex) {
		log.warn("Resource not found: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ResponseDTO<>(false, ex.getMessage(), null));
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ResponseDTO<Void>> handleForbidden(ForbiddenException ex) {
		log.warn("Forbidden: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ResponseDTO<>(false, ex.getMessage(), null));
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ResponseDTO<Void>> handleUnauthorized(UnauthorizedException ex) {
		log.warn("Unauthorized: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ResponseDTO<>(false, ex.getMessage(), null));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ResponseDTO<Void>> handleValidation(MethodArgumentNotValidException ex) {
		String errors = ex.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.collect(Collectors.joining(", "));
		log.warn("Validation failed: {}", errors);
		return ResponseEntity.badRequest().body(new ResponseDTO<>(false, errors, null));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ResponseDTO<Void>> handleAccessDeniedException(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(new ResponseDTO<>(false, "Access denied: User does not have required role or token is invalid", null));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ResponseDTO<Void>> handleGeneralException(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ResponseDTO<>(false, "Internal error: " + ex.getMessage(), null));
	}
}
