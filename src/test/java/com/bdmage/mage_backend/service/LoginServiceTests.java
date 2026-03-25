package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.exception.InvalidCredentialsException;
import com.bdmage.mage_backend.model.AuthProvider;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoginServiceTests {

	@Test
	void loginNormalizesEmailAndReturnsMatchingLocalUser() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		LoginService loginService = new LoginService(userRepository, passwordHashingService);
		User localUser = new User("user@example.com", "hashed-password", "Local User");

		when(userRepository.findByEmailAndAuthProvider("user@example.com", AuthProvider.LOCAL))
				.thenReturn(Optional.of(localUser));
		when(passwordHashingService.matches("plain-password", "hashed-password")).thenReturn(true);

		User authenticatedUser = loginService.login(" User@Example.com ", "plain-password");

		assertThat(authenticatedUser).isSameAs(localUser);
		verify(userRepository).findByEmailAndAuthProvider("user@example.com", AuthProvider.LOCAL);
		verify(passwordHashingService).matches("plain-password", "hashed-password");
	}

	@Test
	void loginRejectsUnknownEmail() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		LoginService loginService = new LoginService(userRepository, passwordHashingService);

		when(userRepository.findByEmailAndAuthProvider("user@example.com", AuthProvider.LOCAL))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> loginService.login("user@example.com", "plain-password"))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Email or password is incorrect.");

		verifyNoInteractions(passwordHashingService);
	}

	@Test
	void loginRejectsWrongPassword() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		LoginService loginService = new LoginService(userRepository, passwordHashingService);
		User localUser = new User("user@example.com", "hashed-password", "Local User");

		when(userRepository.findByEmailAndAuthProvider("user@example.com", AuthProvider.LOCAL))
				.thenReturn(Optional.of(localUser));
		when(passwordHashingService.matches("wrong-password", "hashed-password")).thenReturn(false);

		assertThatThrownBy(() -> loginService.login("user@example.com", "wrong-password"))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Email or password is incorrect.");
	}
}
