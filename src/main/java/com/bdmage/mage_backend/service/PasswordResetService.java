package com.bdmage.mage_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import com.bdmage.mage_backend.exception.InvalidPasswordResetTokenException;
import com.bdmage.mage_backend.model.AuthProvider;
import com.bdmage.mage_backend.model.PasswordResetToken;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.PasswordResetTokenRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

	private static final String INVALID_TOKEN_MESSAGE = "Password reset token is invalid or has expired.";

	// Tokens expire after 15 minutes — long enough for a real user, short enough to limit exposure
	private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

	private static final int TOKEN_BYTE_LENGTH = 32;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final HexFormat HEX_FORMAT = HexFormat.of();

	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final UserRepository userRepository;
	private final PasswordHashingService passwordHashingService;

	public PasswordResetService(
			PasswordResetTokenRepository passwordResetTokenRepository,
			UserRepository userRepository,
			PasswordHashingService passwordHashingService) {
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.userRepository = userRepository;
		this.passwordHashingService = passwordHashingService;
	}

	/**
	 * Initiates a password reset for the given email.
	 *
	 * Returns null when no local account exists so the caller can still return
	 * 200 without leaking whether the address is registered.
	 */
	@Transactional
	public String requestReset(String email) {
		String normalizedEmail = email.trim().toLowerCase();

		Optional<User> maybeUser = this.userRepository.findByEmail(normalizedEmail);

		// No account at all — bail silently, don't confirm or deny the address
		if (maybeUser.isEmpty()) {
			return null;
		}

		User user = maybeUser.get();

		// Google-only accounts have no local password to reset
		if (user.getAuthProvider() == AuthProvider.GOOGLE) {
			return null;
		}

		// Supersede any existing tokens — old reset links stop working once a new one is issued
		this.passwordResetTokenRepository.deleteAllByUserId(user.getId());

		String rawToken = generateRawToken();
		String tokenHash = hashToken(rawToken);
		Instant expiresAt = Instant.now().plus(TOKEN_TTL);

		this.passwordResetTokenRepository.saveAndFlush(
				new PasswordResetToken(user.getId(), tokenHash, expiresAt));

		return rawToken;
	}

	/**
	 * Validates the token and updates the user's password.
	 * Marks the token as used so it cannot be replayed.
	 */
	@Transactional
	public void confirmReset(String rawToken, String newPassword) {
		String tokenHash = hashToken(rawToken);

		PasswordResetToken resetToken = this.passwordResetTokenRepository
				.findByTokenHash(tokenHash)
				.orElseThrow(() -> new InvalidPasswordResetTokenException(INVALID_TOKEN_MESSAGE));

		if (resetToken.isUsed() || resetToken.isExpired()) {
			throw new InvalidPasswordResetTokenException(INVALID_TOKEN_MESSAGE);
		}

		User user = this.userRepository.findById(resetToken.getUserId())
				.orElseThrow(() -> new InvalidPasswordResetTokenException(INVALID_TOKEN_MESSAGE));

		// Mark the token used before touching the password — prevents any retry window
		resetToken.markUsed();
		this.passwordResetTokenRepository.saveAndFlush(resetToken);

		user.linkLocalPassword(this.passwordHashingService.hash(newPassword));
		this.userRepository.saveAndFlush(user);
	}

	public static String hashToken(String rawToken) {
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

}
