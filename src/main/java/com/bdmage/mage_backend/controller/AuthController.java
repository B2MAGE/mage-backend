package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.dto.AccountLinkResponse;
import com.bdmage.mage_backend.dto.GoogleAuthenticationRequest;
import com.bdmage.mage_backend.dto.GoogleAuthenticationResponse;
import com.bdmage.mage_backend.dto.GoogleAccountLinkRequest;
import com.bdmage.mage_backend.dto.LoginRequest;
import com.bdmage.mage_backend.dto.LoginResponse;
import com.bdmage.mage_backend.dto.LocalAccountLinkRequest;
import com.bdmage.mage_backend.dto.RegistrationRequest;
import com.bdmage.mage_backend.dto.RegistrationResponse;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.AccountLinkingService;
import com.bdmage.mage_backend.service.AccountLinkingService.AccountLinkingResult;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import com.bdmage.mage_backend.service.GoogleAuthenticationService;
import com.bdmage.mage_backend.service.GoogleAuthenticationService.GoogleAuthenticationResult;
import com.bdmage.mage_backend.service.LoginService;
import com.bdmage.mage_backend.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/auth", "/api/auth"})
public class AuthController {

	private final AccountLinkingService accountLinkingService;
	private final AuthenticationTokenService authenticationTokenService;
	private final GoogleAuthenticationService googleAuthenticationService;
	private final LoginService loginService;
	private final RegistrationService registrationService;

	public AuthController(
			AccountLinkingService accountLinkingService,
			AuthenticationTokenService authenticationTokenService,
			GoogleAuthenticationService googleAuthenticationService,
			LoginService loginService,
			RegistrationService registrationService) {
		this.accountLinkingService = accountLinkingService;
		this.authenticationTokenService = authenticationTokenService;
		this.googleAuthenticationService = googleAuthenticationService;
		this.loginService = loginService;
		this.registrationService = registrationService;
	}

	@PostMapping("/google")
	ResponseEntity<GoogleAuthenticationResponse> authenticateWithGoogle(
			@Valid @RequestBody GoogleAuthenticationRequest request) {
		GoogleAuthenticationResult result = this.googleAuthenticationService.authenticate(request.idToken());
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		String accessToken = this.authenticationTokenService.issueToken(result.user());

		return ResponseEntity.status(status)
				.body(new GoogleAuthenticationResponse(
						result.user().getId(),
						result.user().getEmail(),
						result.user().getDisplayName(),
						result.user().getAuthProvider().name(),
						result.created(),
						accessToken));
	}

	@PostMapping("/register")
	ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
		User user = this.registrationService.register(
				request.email(),
				request.password(),
				request.displayName());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new RegistrationResponse(
						user.getId(),
						user.getEmail(),
						user.getDisplayName(),
						user.getAuthProvider().name()));
	}

	@PostMapping("/login")
	ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		User user = this.loginService.login(request.email(), request.password());
		String accessToken = this.authenticationTokenService.issueToken(user);

		return ResponseEntity.ok(new LoginResponse(
				user.getId(),
				user.getEmail(),
				user.getDisplayName(),
				user.getAuthProvider().name(),
				accessToken));
	}

	@PostMapping("/link/google")
	ResponseEntity<AccountLinkResponse> linkGoogle(@Valid @RequestBody GoogleAccountLinkRequest request) {
		AccountLinkingResult result = this.accountLinkingService.linkGoogle(
				request.email(),
				request.password(),
				request.idToken());

		return ResponseEntity.ok(toAccountLinkResponse(result));
	}

	@PostMapping("/link/local")
	ResponseEntity<AccountLinkResponse> linkLocal(@Valid @RequestBody LocalAccountLinkRequest request) {
		AccountLinkingResult result = this.accountLinkingService.linkLocal(
				request.idToken(),
				request.password());

		return ResponseEntity.ok(toAccountLinkResponse(result));
	}

	private static AccountLinkResponse toAccountLinkResponse(AccountLinkingResult result) {
		return new AccountLinkResponse(
				result.user().getId(),
				result.user().getEmail(),
				result.user().getDisplayName(),
				result.user().getAuthProvider().name(),
				result.linked());
	}
}
