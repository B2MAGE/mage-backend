package com.bdmage.mage_backend.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.bdmage.mage_backend.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		Map<String, String> details = new LinkedHashMap<>();

		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}

		return buildResponse(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"Request validation failed.",
				details,
				request.getRequestURI());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST",
				"Request body could not be parsed.",
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(InvalidGoogleTokenException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidGoogleToken(
			InvalidGoogleTokenException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.UNAUTHORIZED,
				"INVALID_GOOGLE_TOKEN",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(GoogleAccountConflictException.class)
	ResponseEntity<ApiErrorResponse> handleGoogleAccountConflict(
			GoogleAccountConflictException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.CONFLICT,
				"ACCOUNT_CONFLICT",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(GoogleAuthenticationUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleGoogleAuthenticationUnavailable(
			GoogleAuthenticationUnavailableException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.SERVICE_UNAVAILABLE,
				"GOOGLE_AUTH_UNAVAILABLE",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	private static ResponseEntity<ApiErrorResponse> buildResponse(
			HttpStatus status,
			String code,
			String message,
			Map<String, String> details,
			String path) {
		Map<String, String> responseDetails = details.isEmpty() ? null : details;

		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(
						code,
						message,
						responseDetails,
						path,
						Instant.now()));
	}
}
