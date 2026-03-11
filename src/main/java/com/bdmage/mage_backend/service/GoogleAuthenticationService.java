package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.client.GoogleTokenVerifier;
import com.bdmage.mage_backend.client.GoogleTokenVerifier.VerifiedGoogleToken;
import com.bdmage.mage_backend.exception.GoogleAccountConflictException;
import com.bdmage.mage_backend.exception.InvalidGoogleTokenException;
import com.bdmage.mage_backend.model.AuthProvider;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GoogleAuthenticationService {

	private final GoogleTokenVerifier googleTokenVerifier;
	private final UserRepository userRepository;

	public GoogleAuthenticationService(GoogleTokenVerifier googleTokenVerifier, UserRepository userRepository) {
		this.googleTokenVerifier = googleTokenVerifier;
		this.userRepository = userRepository;
	}

	@Transactional
	public GoogleAuthenticationResult authenticate(String idToken) {
		VerifiedGoogleToken verifiedGoogleToken = this.googleTokenVerifier.verify(idToken)
				.orElseThrow(() -> new InvalidGoogleTokenException("Google ID token is invalid or expired."));

		validateVerifiedGoogleToken(verifiedGoogleToken);

		Optional<User> existingUser = this.userRepository.findByGoogleSubject(verifiedGoogleToken.subject());
		if (existingUser.isPresent()) {
			return new GoogleAuthenticationResult(existingUser.get(), false);
		}

		if (this.userRepository.findByEmailAndAuthProvider(verifiedGoogleToken.email(), AuthProvider.LOCAL).isPresent()) {
			throw new GoogleAccountConflictException(
					"A local account already exists for this email. Account linking is not available yet.");
		}

		if (this.userRepository.findByEmailAndAuthProvider(verifiedGoogleToken.email(), AuthProvider.GOOGLE).isPresent()) {
			throw new GoogleAccountConflictException(
					"A Google-backed account already exists for this email under a different Google identity.");
		}

		User googleUser = User.google(
				verifiedGoogleToken.email(),
				verifiedGoogleToken.subject(),
				resolveDisplayName(verifiedGoogleToken));

		try {
			User savedUser = this.userRepository.saveAndFlush(googleUser);
			return new GoogleAuthenticationResult(savedUser, true);
		} catch (DataIntegrityViolationException ex) {
			Optional<User> concurrentUser = this.userRepository.findByGoogleSubject(verifiedGoogleToken.subject());
			if (concurrentUser.isPresent()) {
				return new GoogleAuthenticationResult(concurrentUser.get(), false);
			}

			throw new GoogleAccountConflictException(
					"Google authentication conflicted with an existing account. Account linking is not available yet.");
		}
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

	private static String resolveDisplayName(VerifiedGoogleToken verifiedGoogleToken) {
		if (StringUtils.hasText(verifiedGoogleToken.displayName())) {
			return verifiedGoogleToken.displayName().trim();
		}

		return verifiedGoogleToken.email();
	}

	public record GoogleAuthenticationResult(User user, boolean created) {
	}
}
