package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidAuthenticationTokenException;
import com.bdmage.mage_backend.model.AuthenticationToken;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.AuthenticationTokenRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticationTokenServiceTests {

	@Test
	void issueTokenGeneratesOpaqueTokenAndStoresOnlyTheHash() {
		AuthenticationTokenRepository authenticationTokenRepository = mock(AuthenticationTokenRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		AuthenticationTokenService authenticationTokenService = new AuthenticationTokenService(
				authenticationTokenRepository,
				userRepository);
		User user = new User("user@example.com", "hashed-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);

		String rawToken = authenticationTokenService.issueToken(user);
		ArgumentCaptor<AuthenticationToken> tokenCaptor = ArgumentCaptor.forClass(AuthenticationToken.class);

		verify(authenticationTokenRepository).saveAndFlush(tokenCaptor.capture());
		assertThat(rawToken).isNotBlank();
		assertThat(tokenCaptor.getValue().getUserId()).isEqualTo(42L);
		assertThat(tokenCaptor.getValue().getTokenHash()).hasSize(64);
		assertThat(tokenCaptor.getValue().getTokenHash()).isEqualTo(AuthenticationTokenService.hashToken(rawToken));
		assertThat(tokenCaptor.getValue().getTokenHash()).isNotEqualTo(rawToken);
		verifyNoInteractions(userRepository);
	}

	@Test
	void authenticateReturnsUserForKnownToken() {
		AuthenticationTokenRepository authenticationTokenRepository = mock(AuthenticationTokenRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		AuthenticationTokenService authenticationTokenService = new AuthenticationTokenService(
				authenticationTokenRepository,
				userRepository);
		User user = new User("user@example.com", "hashed-password", "User");
		String rawToken = "known-token";
		String tokenHash = AuthenticationTokenService.hashToken(rawToken);

		when(authenticationTokenRepository.findByTokenHash(tokenHash))
				.thenReturn(Optional.of(new AuthenticationToken(42L, tokenHash)));
		when(userRepository.findById(42L)).thenReturn(Optional.of(user));

		User authenticatedUser = authenticationTokenService.authenticate(rawToken);

		assertThat(authenticatedUser).isSameAs(user);
		verify(authenticationTokenRepository).findByTokenHash(tokenHash);
		verify(userRepository).findById(42L);
	}

	@Test
	void authenticateRejectsMissingToken() {
		AuthenticationTokenRepository authenticationTokenRepository = mock(AuthenticationTokenRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		AuthenticationTokenService authenticationTokenService = new AuthenticationTokenService(
				authenticationTokenRepository,
				userRepository);

		assertThatThrownBy(() -> authenticationTokenService.authenticate(" "))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verifyNoInteractions(authenticationTokenRepository, userRepository);
	}

	@Test
	void authenticateRejectsUnknownToken() {
		AuthenticationTokenRepository authenticationTokenRepository = mock(AuthenticationTokenRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		AuthenticationTokenService authenticationTokenService = new AuthenticationTokenService(
				authenticationTokenRepository,
				userRepository);
		String rawToken = "unknown-token";

		when(authenticationTokenRepository.findByTokenHash(AuthenticationTokenService.hashToken(rawToken)))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> authenticationTokenService.authenticate(rawToken))
				.isInstanceOf(InvalidAuthenticationTokenException.class)
				.hasMessage("Authentication token is invalid.");

		verify(authenticationTokenRepository).findByTokenHash(AuthenticationTokenService.hashToken(rawToken));
		verifyNoInteractions(userRepository);
	}
}
