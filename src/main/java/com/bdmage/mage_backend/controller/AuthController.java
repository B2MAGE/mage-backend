package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.dto.GoogleAuthenticationRequest;
import com.bdmage.mage_backend.dto.GoogleAuthenticationResponse;
import com.bdmage.mage_backend.service.GoogleAuthenticationService;
import com.bdmage.mage_backend.service.GoogleAuthenticationService.GoogleAuthenticationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final GoogleAuthenticationService googleAuthenticationService;

	public AuthController(GoogleAuthenticationService googleAuthenticationService) {
		this.googleAuthenticationService = googleAuthenticationService;
	}

	@PostMapping("/google")
	ResponseEntity<GoogleAuthenticationResponse> authenticateWithGoogle(
			@Valid @RequestBody GoogleAuthenticationRequest request) {
		GoogleAuthenticationResult result = this.googleAuthenticationService.authenticate(request.idToken());
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;

		return ResponseEntity.status(status)
				.body(new GoogleAuthenticationResponse(
						result.user().getId(),
						result.user().getEmail(),
						result.user().getDisplayName(),
						result.user().getAuthProvider().name(),
						result.created()));
	}
}
