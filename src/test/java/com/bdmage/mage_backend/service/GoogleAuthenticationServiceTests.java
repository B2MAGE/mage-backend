package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.client.GoogleTokenVerifier;
import com.bdmage.mage_backend.client.GoogleTokenVerifier.VerifiedGoogleToken;
import com.bdmage.mage_backend.exception.AccountConflictException;
import com.bdmage.mage_backend.exception.AccountLinkRequiredException;
import com.bdmage.mage_backend.exception.InvalidGoogleTokenException;
import com.bdmage.mage_backend.model.AuthProvider;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleAuthenticationServiceTests {

	@Test
	void createsGoogleUserOnFirstSuccessfulAuthentication() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		GoogleAuthenticationService googleAuthenticationService = new GoogleAuthenticationService(
				googleTokenVerifier,
				userRepository);

		VerifiedGoogleToken verifiedGoogleToken = new VerifiedGoogleToken(
				"google-subject-1",
				"user@example.com",
				true,
				"Google User");
		User savedUser = User.google("user@example.com", "google-subject-1", "Google User");
		ReflectionTestUtils.setField(savedUser, "id", 42L);

		when(googleTokenVerifier.verify("valid-token")).thenReturn(Optional.of(verifiedGoogleToken));
		when(userRepository.findByGoogleSubject("google-subject-1")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
		when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);

		GoogleAuthenticationService.GoogleAuthenticationResult result = googleAuthenticationService.authenticate(
				"valid-token");

		assertThat(result.created()).isTrue();
		assertThat(result.user().getId()).isEqualTo(42L);
		assertThat(result.user().getEmail()).isEqualTo("user@example.com");
		assertThat(result.user().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
		verify(userRepository).saveAndFlush(any(User.class));
	}

	@Test
	void reusesExistingGoogleUserWhenSubjectAlreadyExists() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		GoogleAuthenticationService googleAuthenticationService = new GoogleAuthenticationService(
				googleTokenVerifier,
				userRepository);

		VerifiedGoogleToken verifiedGoogleToken = new VerifiedGoogleToken(
				"google-subject-2",
				"user@example.com",
				true,
				"Google User");
		User existingUser = User.google("user@example.com", "google-subject-2", "Google User");
		ReflectionTestUtils.setField(existingUser, "id", 84L);

		when(googleTokenVerifier.verify("repeat-token")).thenReturn(Optional.of(verifiedGoogleToken));
		when(userRepository.findByGoogleSubject("google-subject-2")).thenReturn(Optional.of(existingUser));

		GoogleAuthenticationService.GoogleAuthenticationResult result = googleAuthenticationService.authenticate(
				"repeat-token");

		assertThat(result.created()).isFalse();
		assertThat(result.user().getId()).isEqualTo(84L);
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void rejectsInvalidGoogleToken() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		GoogleAuthenticationService googleAuthenticationService = new GoogleAuthenticationService(
				googleTokenVerifier,
				userRepository);

		when(googleTokenVerifier.verify("invalid-token")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> googleAuthenticationService.authenticate("invalid-token"))
				.isInstanceOf(InvalidGoogleTokenException.class)
				.hasMessage("Google ID token is invalid or expired.");
	}

	@Test
	void rejectsUnverifiedEmailClaims() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		GoogleAuthenticationService googleAuthenticationService = new GoogleAuthenticationService(
				googleTokenVerifier,
				userRepository);

		when(googleTokenVerifier.verify("unverified-token")).thenReturn(Optional.of(
				new VerifiedGoogleToken("google-subject-3", "user@example.com", false, "Google User")));

		assertThatThrownBy(() -> googleAuthenticationService.authenticate("unverified-token"))
				.isInstanceOf(InvalidGoogleTokenException.class)
				.hasMessage("Google account email is not verified.");
	}

	@Test
	void rejectsConflictsWithExistingLocalAccounts() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		GoogleAuthenticationService googleAuthenticationService = new GoogleAuthenticationService(
				googleTokenVerifier,
				userRepository);

		when(googleTokenVerifier.verify("conflict-token")).thenReturn(Optional.of(
				new VerifiedGoogleToken("google-subject-4", "user@example.com", true, "Google User")));
		when(userRepository.findByGoogleSubject("google-subject-4")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("user@example.com"))
				.thenReturn(Optional.of(new User("user@example.com", "password-hash", "Local User")));

		assertThatThrownBy(() -> googleAuthenticationService.authenticate("conflict-token"))
				.isInstanceOf(AccountLinkRequiredException.class)
				.hasMessage(
						"A local account already exists for this email. Link Google through /api/auth/link/google after authenticating that local account.");
	}

	@Test
	void rejectsConflictsWithExistingGoogleAccountUsingSameEmail() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		GoogleAuthenticationService googleAuthenticationService = new GoogleAuthenticationService(
				googleTokenVerifier,
				userRepository);

		when(googleTokenVerifier.verify("same-email-conflict-token")).thenReturn(Optional.of(
				new VerifiedGoogleToken("google-subject-4b", "user@example.com", true, "Google User")));
		when(userRepository.findByGoogleSubject("google-subject-4b")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("user@example.com"))
				.thenReturn(Optional.of(User.google("user@example.com", "google-subject-existing", "Existing Google User")));

		assertThatThrownBy(() -> googleAuthenticationService.authenticate("same-email-conflict-token"))
				.isInstanceOf(AccountConflictException.class)
				.hasMessage("A Google-backed account already exists for this email under a different Google identity.");
	}

	@Test
	void reusesConcurrentGoogleUserWhenInsertLosesRace() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		GoogleAuthenticationService googleAuthenticationService = new GoogleAuthenticationService(
				googleTokenVerifier,
				userRepository);

		VerifiedGoogleToken verifiedGoogleToken = new VerifiedGoogleToken(
				"google-subject-5",
				"user@example.com",
				true,
				"Google User");
		User existingUser = User.google("user@example.com", "google-subject-5", "Google User");
		ReflectionTestUtils.setField(existingUser, "id", 128L);

		when(googleTokenVerifier.verify("race-token")).thenReturn(Optional.of(verifiedGoogleToken));
		when(userRepository.findByGoogleSubject("google-subject-5"))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(existingUser));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
		when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

		GoogleAuthenticationService.GoogleAuthenticationResult result = googleAuthenticationService.authenticate("race-token");

		assertThat(result.created()).isFalse();
		assertThat(result.user().getId()).isEqualTo(128L);
	}
}

