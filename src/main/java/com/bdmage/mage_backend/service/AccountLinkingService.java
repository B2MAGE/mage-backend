package com.bdmage.mage_backend.service;

import java.util.Locale;
import java.util.Optional;

import com.bdmage.mage_backend.client.GoogleTokenVerifier;
import com.bdmage.mage_backend.client.GoogleTokenVerifier.VerifiedGoogleToken;
import com.bdmage.mage_backend.exception.AccountConflictException;
import com.bdmage.mage_backend.exception.AccountLinkRequiredException;
import com.bdmage.mage_backend.exception.InvalidGoogleTokenException;
import com.bdmage.mage_backend.exception.InvalidLocalCredentialsException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountLinkingService {

	private final GoogleTokenVerifier googleTokenVerifier;
	private final UserRepository userRepository;
	private final PasswordHashingService passwordHashingService;

	public AccountLinkingService(
			GoogleTokenVerifier googleTokenVerifier,
			UserRepository userRepository,
			PasswordHashingService passwordHashingService) {
		this.googleTokenVerifier = googleTokenVerifier;
		this.userRepository = userRepository;
		this.passwordHashingService = passwordHashingService;
	}

	@Transactional
	public AccountLinkingResult linkGoogle(String email, String plainPassword, String idToken) {
		String normalisedEmail = normalizeEmail(email);
		VerifiedGoogleToken verifiedGoogleToken = verifyGoogleToken(idToken);
		String normalisedGoogleEmail = normalizeEmail(verifiedGoogleToken.email());

		if (!normalisedEmail.equals(normalisedGoogleEmail)) {
			throw new AccountConflictException(
					"The Google account email must match the local account email before linking.");
		}

		User localUser = this.userRepository.findByEmail(normalisedEmail)
				.filter(User::supportsLocalAuthentication)
				.orElseThrow(() -> new InvalidLocalCredentialsException("Email or password is incorrect."));

		if (!this.passwordHashingService.matches(plainPassword, localUser.getPasswordHash())) {
			throw new InvalidLocalCredentialsException("Email or password is incorrect.");
		}

		Optional<User> existingGoogleUser = this.userRepository.findByGoogleSubject(verifiedGoogleToken.subject());
		if (existingGoogleUser.isPresent()) {
			User linkedGoogleUser = existingGoogleUser.get();
			if (!linkedGoogleUser.getId().equals(localUser.getId())) {
				throw new AccountConflictException("This Google identity is already linked to another account.");
			}
			return new AccountLinkingResult(localUser, false);
		}

		if (localUser.supportsGoogleAuthentication()) {
			throw new AccountConflictException("This account is already linked to a different Google identity.");
		}

		localUser.linkGoogle(verifiedGoogleToken.subject());
		User savedUser = this.userRepository.saveAndFlush(localUser);
		return new AccountLinkingResult(savedUser, true);
	}

	@Transactional
	public AccountLinkingResult linkLocal(String idToken, String plainPassword) {
		VerifiedGoogleToken verifiedGoogleToken = verifyGoogleToken(idToken);
		String normalisedGoogleEmail = normalizeEmail(verifiedGoogleToken.email());

		Optional<User> googleUser = this.userRepository.findByGoogleSubject(verifiedGoogleToken.subject());
		if (googleUser.isEmpty()) {
			Optional<User> existingUser = this.userRepository.findByEmail(normalisedGoogleEmail);
			if (existingUser.isPresent()) {
				User user = existingUser.get();
				if (user.supportsLocalAuthentication() && !user.supportsGoogleAuthentication()) {
					throw new AccountLinkRequiredException(
							"A local account already exists for this email. Link Google through /auth/link/google after authenticating that local account.");
				}
				if (user.supportsGoogleAuthentication()) {
					throw new AccountConflictException(
							"A Google-backed account already exists for this email under a different Google identity.");
				}
			}

			throw new AccountLinkRequiredException(
					"Authenticate with Google through /auth/google before linking local authentication.");
		}

		User user = googleUser.get();
		if (!normalizeEmail(user.getEmail()).equals(normalisedGoogleEmail)) {
			throw new AccountConflictException(
					"The Google account email must match the stored account email before local authentication can be linked.");
		}

		if (user.supportsLocalAuthentication()) {
			if (this.passwordHashingService.matches(plainPassword, user.getPasswordHash())) {
				return new AccountLinkingResult(user, false);
			}

			throw new AccountConflictException("Local authentication is already linked for this account.");
		}

		String passwordHash = this.passwordHashingService.hash(plainPassword);
		user.linkLocalPassword(passwordHash);
		User savedUser = this.userRepository.saveAndFlush(user);
		return new AccountLinkingResult(savedUser, true);
	}

	private VerifiedGoogleToken verifyGoogleToken(String idToken) {
		VerifiedGoogleToken verifiedGoogleToken = this.googleTokenVerifier.verify(idToken)
				.orElseThrow(() -> new InvalidGoogleTokenException("Google ID token is invalid or expired."));

		validateVerifiedGoogleToken(verifiedGoogleToken);
		return verifiedGoogleToken;
	}

	private static void validateVerifiedGoogleToken(VerifiedGoogleToken verifiedGoogleToken) {
		if (!StringUtils.hasText(verifiedGoogleToken.subject())) {
			throw new InvalidGoogleTokenException("Google ID token is missing a subject.");
		}

		if (!StringUtils.hasText(verifiedGoogleToken.email())) {
			throw new InvalidGoogleTokenException("Google ID token is missing an email address.");
		}

		if (!verifiedGoogleToken.emailVerified()) {
			throw new InvalidGoogleTokenException("Google account email is not verified.");
		}
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public record AccountLinkingResult(User user, boolean linked) {
	}
}
