package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.dto.PasswordResetConfirmRequest;
import com.bdmage.mage_backend.dto.PasswordResetRequest;
import com.bdmage.mage_backend.dto.PasswordResetResponse;
import com.bdmage.mage_backend.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

	private final PasswordResetService passwordResetService;

	public PasswordResetController(PasswordResetService passwordResetService) {
		this.passwordResetService = passwordResetService;
	}

	// POST /api/auth/reset-password/request
	// Always returns 200 with the same message so we don't leak whether an email is registered
	@PostMapping("/reset-password/request")
	ResponseEntity<PasswordResetResponse> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
		String rawToken = this.passwordResetService.requestReset(request.email());

		// In a real deployment the token would be emailed and not returned here.
		// We include it in the response body so the confirm flow can be tested without an email server.
		return ResponseEntity.ok(new PasswordResetResponse(
				"If an account with that email exists, a password reset link has been sent.",
				rawToken));
	}

	// POST /api/auth/reset-password/confirm
	@PostMapping("/reset-password/confirm")
	ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
		this.passwordResetService.confirmReset(request.resetToken(), request.newPassword());
		return ResponseEntity.ok().build();
	}

}
