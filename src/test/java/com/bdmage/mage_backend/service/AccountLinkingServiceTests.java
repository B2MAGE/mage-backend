package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.client.GoogleTokenVerifier;
import com.bdmage.mage_backend.client.GoogleTokenVerifier.VerifiedGoogleToken;
import com.bdmage.mage_backend.exception.AccountConflictException;
import com.bdmage.mage_backend.exception.AccountLinkRequiredException;
import com.bdmage.mage_backend.exception.InvalidLocalCredentialsException;
import com.bdmage.mage_backend.model.AuthProvider;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountLinkingServiceTests {

	@Test
	void linkGoogleAddsGoogleProviderToExistingLocalAccount() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		AccountLinkingService accountLinkingService = new AccountLinkingService(
				googleTokenVerifier,
				userRepository,
				passwordHashingService);

		User localUser = new User("user@example.com", "stored-password-hash", "Local User");
		ReflectionTestUtils.setField(localUser, "id", 51L);

		when(googleTokenVerifier.verify("valid-token")).thenReturn(Optional.of(
				new VerifiedGoogleToken("google-subject-1", "user@example.com", true, "Google User")));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(localUser));
		when(passwordHashingService.matches("secret-value", "stored-password-hash")).thenReturn(true);
		when(userRepository.findByGoogleSubject("google-subject-1")).thenReturn(Optional.empty());
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));

		AccountLinkingService.AccountLinkingResult result = accountLinkingService.linkGoogle(
				"user@example.com",
				"secret-value",
				"valid-token");

		assertThat(result.linked()).isTrue();
		assertThat(result.user().getId()).isEqualTo(51L);
		assertThat(result.user().getAuthProvider()).isEqualTo(AuthProvider.LOCAL_GOOGLE);
		assertThat(result.user().getGoogleSubject()).isEqualTo("google-subject-1");
		verify(userRepository).saveAndFlush(localUser);
	}

	@Test
	void linkGoogleRejectsMismatchedGoogleEmail() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		AccountLinkingService accountLinkingService = new AccountLinkingService(
				googleTokenVerifier,
				userRepository,
				passwordHashingService);

		when(googleTokenVerifier.verify("valid-token")).thenReturn(Optional.of(
				new VerifiedGoogleToken("google-subject-1", "other@example.com", true, "Google User")));

		assertThatThrownBy(() -> accountLinkingService.linkGoogle("user@example.com", "secret-value", "valid-token"))
				.isInstanceOf(AccountConflictException.class)
				.hasMessage("The Google account email must match the local account email before linking.");
	}

	@Test
	void linkGoogleRejectsInvalidLocalCredentials() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		AccountLinkingService accountLinkingService = new AccountLinkingService(
				googleTokenVerifier,
				userRepository,
				passwordHashingService);

		User localUser = new User("user@example.com", "stored-password-hash", "Local User");

		when(googleTokenVerifier.verify("valid-token")).thenReturn(Optional.of(
				new VerifiedGoogleToken("google-subject-1", "user@example.com", true, "Google User")));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(localUser));
		when(passwordHashingService.matches("secret-value", "stored-password-hash")).thenReturn(false);

		assertThatThrownBy(() -> accountLinkingService.linkGoogle("user@example.com", "secret-value", "valid-token"))
				.isInstanceOf(InvalidLocalCredentialsException.class)
				.hasMessage("Email or password is incorrect.");
	}

	@Test
	void linkLocalAddsLocalProviderToExistingGoogleAccount() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		AccountLinkingService accountLinkingService = new AccountLinkingService(
				googleTokenVerifier,
				userRepository,
				passwordHashingService);

		User googleUser = User.google("user@example.com", "google-subject-2", "Google User");
		ReflectionTestUtils.setField(googleUser, "id", 61L);

		when(googleTokenVerifier.verify("valid-token")).thenReturn(Optional.of(
				new VerifiedGoogleToken("google-subject-2", "user@example.com", true, "Google User")));
		when(userRepository.findByGoogleSubject("google-subject-2")).thenReturn(Optional.of(googleUser));
		when(passwordHashingService.hash("secret-value")).thenReturn("new-password-hash");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));

		AccountLinkingService.AccountLinkingResult result = accountLinkingService.linkLocal("valid-token", "secret-value");

		assertThat(result.linked()).isTrue();
		assertThat(result.user().getId()).isEqualTo(61L);
		assertThat(result.user().getAuthProvider()).isEqualTo(AuthProvider.LOCAL_GOOGLE);
		assertThat(result.user().getPasswordHash()).isEqualTo("new-password-hash");
		verify(userRepository).saveAndFlush(googleUser);
	}

	@Test
	void linkLocalRequiresExistingGoogleAuthenticationContext() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		AccountLinkingService accountLinkingService = new AccountLinkingService(
				googleTokenVerifier,
				userRepository,
				passwordHashingService);

		when(googleTokenVerifier.verify("valid-token")).thenReturn(Optional.of(
				new VerifiedGoogleToken("google-subject-3", "user@example.com", true, "Google User")));
		when(userRepository.findByGoogleSubject("google-subject-3")).thenReturn(Optional.empty());
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> accountLinkingService.linkLocal("valid-token", "secret-value"))
				.isInstanceOf(AccountLinkRequiredException.class)
				.hasMessage("Authenticate with Google through /auth/google before linking local authentication.");
	}

	@Test
	void linkLocalReturnsExistingLinkedAccountWhenPasswordAlreadyMatches() {
		GoogleTokenVerifier googleTokenVerifier = mock(GoogleTokenVerifier.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		AccountLinkingService accountLinkingService = new AccountLinkingService(
				googleTokenVerifier,
				userRepository,
				passwordHashingService);

		User linkedUser = User.localAndGoogle(
				"user@example.com",
				"stored-password-hash",
				"google-subject-4",
				"Linked User");
		ReflectionTestUtils.setField(linkedUser, "id", 71L);

		when(googleTokenVerifier.verify("valid-token")).thenReturn(Optional.of(
				new VerifiedGoogleToken("google-subject-4", "user@example.com", true, "Google User")));
		when(userRepository.findByGoogleSubject("google-subject-4")).thenReturn(Optional.of(linkedUser));
		when(passwordHashingService.matches("secret-value", "stored-password-hash")).thenReturn(true);

		AccountLinkingService.AccountLinkingResult result = accountLinkingService.linkLocal("valid-token", "secret-value");

		assertThat(result.linked()).isFalse();
		assertThat(result.user().getId()).isEqualTo(71L);
		assertThat(result.user().getAuthProvider()).isEqualTo(AuthProvider.LOCAL_GOOGLE);
	}
}
