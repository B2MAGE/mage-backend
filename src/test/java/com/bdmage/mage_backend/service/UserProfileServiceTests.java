package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidCurrentPasswordException;
import com.bdmage.mage_backend.exception.LocalPasswordChangeUnavailableException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserProfileServiceTests {

	@Test
	void getAuthenticatedUserReturnsMatchingUser() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);
		User user = new User("user@example.com", "hashed-password", "Profile User");

		when(userRepository.findById(42L)).thenReturn(Optional.of(user));

		User authenticatedUser = userProfileService.getAuthenticatedUser(42L);

		assertThat(authenticatedUser).isSameAs(user);
		verify(userRepository).findById(42L);
	}

	@Test
	void getAuthenticatedUserRejectsMissingRequestIdentity() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);

		assertThatThrownBy(() -> userProfileService.getAuthenticatedUser(null))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verifyNoInteractions(userRepository);
	}

	@Test
	void getAuthenticatedUserRejectsUnknownRequestIdentity() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);

		when(userRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userProfileService.getAuthenticatedUser(99L))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verify(userRepository).findById(99L);
	}

	@Test
	void updateAuthenticatedUserProfileTrimsAndPersistsNames() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);
		User user = new User("user@example.com", "hashed-password", "Profile", "User", "Profile User");

		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(userRepository.saveAndFlush(user)).thenReturn(user);

		User updatedUser = userProfileService.updateAuthenticatedUserProfile(
				42L,
				" Updated ",
				" Name ",
				" Updated Profile ");

		assertThat(updatedUser.getFirstName()).isEqualTo("Updated");
		assertThat(updatedUser.getLastName()).isEqualTo("Name");
		assertThat(updatedUser.getDisplayName()).isEqualTo("Updated Profile");
		verify(userRepository).findById(42L);
		verify(userRepository).saveAndFlush(user);
	}

	@Test
	void changeAuthenticatedUserPasswordHashesAndPersistsNewPassword() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);
		User user = new User("user@example.com", "stored-password-hash", "Profile", "User", "Profile User");

		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(passwordHashingService.matches("current-password", "stored-password-hash")).thenReturn(true);
		when(passwordHashingService.hash("new-password")).thenReturn("new-password-hash");

		userProfileService.changeAuthenticatedUserPassword(42L, "current-password", "new-password");

		assertThat(user.getPasswordHash()).isEqualTo("new-password-hash");
		verify(userRepository).findById(42L);
		verify(passwordHashingService).matches("current-password", "stored-password-hash");
		verify(passwordHashingService).hash("new-password");
		verify(userRepository).saveAndFlush(user);
	}

	@Test
	void changeAuthenticatedUserPasswordRejectsInvalidCurrentPassword() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);
		User user = new User("user@example.com", "stored-password-hash", "Profile", "User", "Profile User");

		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(passwordHashingService.matches("wrong-password", "stored-password-hash")).thenReturn(false);

		assertThatThrownBy(() ->
				userProfileService.changeAuthenticatedUserPassword(42L, "wrong-password", "new-password"))
				.isInstanceOf(InvalidCurrentPasswordException.class)
				.hasMessage("Current password is incorrect.");
	}

	@Test
	void changeAuthenticatedUserPasswordRejectsGoogleOnlyAccounts() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);
		User user = User.google("user@example.com", "google-subject", "Profile", "User", "Profile User");

		when(userRepository.findById(42L)).thenReturn(Optional.of(user));

		assertThatThrownBy(() ->
				userProfileService.changeAuthenticatedUserPassword(42L, "current-password", "new-password"))
				.isInstanceOf(LocalPasswordChangeUnavailableException.class)
				.hasMessage("Local password changes are not available for this account.");
	}
}
