package com.bdmage.mage_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidAuthenticationTokenException;
import com.bdmage.mage_backend.model.AuthenticationToken;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.AuthenticationTokenRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthenticationTokenService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String INVALID_AUTHENTICATION_TOKEN_MESSAGE = "Authentication token is invalid.";
	private static final int TOKEN_BYTE_LENGTH = 32;
	private static final int MAX_TOKEN_GENERATION_ATTEMPTS = 3;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final HexFormat HEX_FORMAT = HexFormat.of();

	private final AuthenticationTokenRepository authenticationTokenRepository;
	private final UserRepository userRepository;

	public AuthenticationTokenService(
			AuthenticationTokenRepository authenticationTokenRepository,
			UserRepository userRepository) {
		this.authenticationTokenRepository = authenticationTokenRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public String issueToken(User user) {
		Long userId = user.getId();
		if (userId == null) {
			throw new IllegalArgumentException("Authentication tokens require a persisted user.");
		}

		for (int attempt = 0; attempt < MAX_TOKEN_GENERATION_ATTEMPTS; attempt++) {
			String rawToken = generateRawToken();
			try {
				this.authenticationTokenRepository.saveAndFlush(new AuthenticationToken(
						userId,
						hashToken(rawToken)));
				return rawToken;
			} catch (DataIntegrityViolationException ex) {
				if (attempt == MAX_TOKEN_GENERATION_ATTEMPTS - 1) {
					throw ex;
				}
			}
		}

		throw new IllegalStateException("Authentication token generation failed.");
	}

	@Transactional(readOnly = true)
	public User authenticate(String rawToken) {
		if (!StringUtils.hasText(rawToken)) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}

		AuthenticationToken authenticationToken = this.authenticationTokenRepository.findByTokenHash(hashToken(rawToken))
				.orElseThrow(() -> new InvalidAuthenticationTokenException(INVALID_AUTHENTICATION_TOKEN_MESSAGE));

		return this.userRepository.findById(authenticationToken.getUserId())
				.orElseThrow(() -> new InvalidAuthenticationTokenException(INVALID_AUTHENTICATION_TOKEN_MESSAGE));
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
}
