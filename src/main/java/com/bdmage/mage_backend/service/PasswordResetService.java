package com.bdmage.mage_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import com.bdmage.mage_backend.config.PasswordResetProperties;
import com.bdmage.mage_backend.exception.InvalidPasswordResetTokenException;
import com.bdmage.mage_backend.model.PasswordResetToken;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.AuthenticationTokenRepository;
import com.bdmage.mage_backend.repository.PasswordResetTokenRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {

	public static final String RESET_REQUEST_MESSAGE =
			"If an account with that email exists, a password reset link has been sent.";
	public static final String RESET_CONFIRM_MESSAGE = "Password has been reset.";

	private static final String INVALID_TOKEN_MESSAGE = "Password reset link is invalid or expired.";
	private static final int TOKEN_BYTE_LENGTH = 32;
	private static final int MAX_TOKEN_GENERATION_ATTEMPTS = 3;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final HexFormat HEX_FORMAT = HexFormat.of();

	private final AuthenticationTokenRepository authenticationTokenRepository;
	private final Clock clock;
	private final PasswordHashingService passwordHashingService;
	private final PasswordResetEmailSender passwordResetEmailSender;
	private final PasswordResetProperties passwordResetProperties;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final UserRepository userRepository;

	public PasswordResetService(
			AuthenticationTokenRepository authenticationTokenRepository,
			Clock clock,
			PasswordHashingService passwordHashingService,
			PasswordResetEmailSender passwordResetEmailSender,
			PasswordResetProperties passwordResetProperties,
			PasswordResetTokenRepository passwordResetTokenRepository,
			UserRepository userRepository) {
		this.authenticationTokenRepository = authenticationTokenRepository;
		this.clock = clock;
		this.passwordHashingService = passwordHashingService;
		this.passwordResetEmailSender = passwordResetEmailSender;
		this.passwordResetProperties = passwordResetProperties;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public void requestPasswordReset(String email) {
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		this.userRepository.findByEmail(normalizedEmail)
				.filter(User::supportsLocalAuthentication)
				.ifPresent(this::createAndSendResetLink);
	}

	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		Instant now = Instant.now(this.clock);
		PasswordResetToken resetToken = this.passwordResetTokenRepository.findByTokenHash(hashToken(rawToken.trim()))
				.filter(token -> token.isUsableAt(now))
				.orElseThrow(() -> new InvalidPasswordResetTokenException(INVALID_TOKEN_MESSAGE));

		User user = this.userRepository.findById(resetToken.getUserId())
				.filter(User::supportsLocalAuthentication)
				.orElseThrow(() -> new InvalidPasswordResetTokenException(INVALID_TOKEN_MESSAGE));

		user.updateLocalPassword(this.passwordHashingService.hash(newPassword));
		resetToken.markUsed(now);
		this.authenticationTokenRepository.deleteByUserId(user.getId());
		this.userRepository.saveAndFlush(user);
		this.passwordResetTokenRepository.saveAndFlush(resetToken);
	}

	private void createAndSendResetLink(User user) {
		this.passwordResetTokenRepository.deleteByUserIdAndUsedAtIsNull(user.getId());
		Instant expiresAt = Instant.now(this.clock).plus(this.passwordResetProperties.resolvedTokenTtl());
		PasswordResetIssue issuedToken = issueResetToken(user, expiresAt);
		this.passwordResetEmailSender.sendPasswordResetLink(
				user,
				buildResetLink(issuedToken.rawToken()),
				issuedToken.expiresAt());
	}

	private PasswordResetIssue issueResetToken(User user, Instant expiresAt) {
		for (int attempt = 0; attempt < MAX_TOKEN_GENERATION_ATTEMPTS; attempt++) {
			String rawToken = generateRawToken();
			try {
				this.passwordResetTokenRepository.saveAndFlush(new PasswordResetToken(
						user.getId(),
						hashToken(rawToken),
						expiresAt));
				return new PasswordResetIssue(rawToken, expiresAt);
			} catch (DataIntegrityViolationException ex) {
				if (attempt == MAX_TOKEN_GENERATION_ATTEMPTS - 1) {
					throw ex;
				}
			}
		}

		throw new IllegalStateException("Password reset token generation failed.");
	}

	private String buildResetLink(String rawToken) {
		return UriComponentsBuilder.fromUriString(this.passwordResetProperties.normalizedFrontendBaseUrl())
				.path("/reset-password")
				.queryParam("token", rawToken)
				.build()
				.toUriString();
	}

	static String hashToken(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HEX_FORMAT.formatHex(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable.", ex);
		}
	}

	private static String generateRawToken() {
		byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
		SECURE_RANDOM.nextBytes(tokenBytes);
		return TOKEN_ENCODER.encodeToString(tokenBytes);
	}

	private record PasswordResetIssue(String rawToken, Instant expiresAt) {
	}
}
