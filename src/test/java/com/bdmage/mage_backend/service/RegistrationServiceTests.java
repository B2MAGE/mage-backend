package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.exception.AccountLinkRequiredException;
import com.bdmage.mage_backend.exception.EmailAlreadyRegisteredException;
import com.bdmage.mage_backend.model.AuthProvider;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceTests {

	@Test
	void registerNormalizesEmailHashesPasswordAndPersistsLocalUser() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		RegistrationService registrationService = new RegistrationService(userRepository, passwordHashingService);

		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
		when(passwordHashingService.hash("plain-password")).thenReturn("hashed-password");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));

		User registeredUser = registrationService.register(" User@Example.com ", "plain-password", " New User ");

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(userCaptor.capture());

		User savedUser = userCaptor.getValue();
		assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
		assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
		assertThat(savedUser.getDisplayName()).isEqualTo("New User");
		assertThat(savedUser.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);

		assertThat(registeredUser.getEmail()).isEqualTo("user@example.com");
		assertThat(registeredUser.getPasswordHash()).isEqualTo("hashed-password");
		assertThat(registeredUser.getDisplayName()).isEqualTo("New User");
	}

	@Test
	void registerRejectsDuplicateLocalEmail() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		RegistrationService registrationService = new RegistrationService(userRepository, passwordHashingService);

		when(userRepository.findByEmail("user@example.com"))
				.thenReturn(Optional.of(new User("user@example.com", "existing-hash", "Existing User")));

		assertThatThrownBy(() -> registrationService.register("user@example.com", "plain-password", "New User"))
				.isInstanceOf(EmailAlreadyRegisteredException.class)
				.hasMessage("Local authentication is already configured for this email.");

		verify(passwordHashingService, never()).hash(any());
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	void registerRequiresExplicitLinkWhenEmailBelongsToGoogleAccount() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		RegistrationService registrationService = new RegistrationService(userRepository, passwordHashingService);

		when(userRepository.findByEmail("user@example.com"))
				.thenReturn(Optional.of(User.google("user@example.com", "google-subject-1", "Google User")));

		assertThatThrownBy(() -> registrationService.register("user@example.com", "plain-password", "New User"))
				.isInstanceOf(AccountLinkRequiredException.class)
				.hasMessage(
						"A Google-backed account already exists for this email. Link local authentication through /api/auth/link/local after authenticating with Google.");

		verify(passwordHashingService, never()).hash(any());
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}
}

