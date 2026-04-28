package com.bdmage.mage_backend.exception;

import java.time.Instant;
import java.util.Map;

import com.bdmage.mage_backend.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Separate advice class so we don't have to touch the existing ApiExceptionHandler
@RestControllerAdvice
public class PasswordResetExceptionHandler {

	// 400 — token not found, already used, or past its expiry window
	@ExceptionHandler(InvalidPasswordResetTokenException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidPasswordResetToken(
			InvalidPasswordResetTokenException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorResponse(
						"INVALID_PASSWORD_RESET_TOKEN",
						ex.getMessage(),
						null,
						request.getRequestURI(),
						Instant.now()));
	}

}
