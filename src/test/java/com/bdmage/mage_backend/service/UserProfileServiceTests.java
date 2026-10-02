package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidCurrentPasswordException;
import com.bdmage.mage_backend.exception.LocalPasswordChangeUnavailableException;
import com.bdmage.mage_backend.exception.HandleAlreadyInUseException;
import com.bdmage.mage_backend.exception.ProfileNotFoundException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserProfileServiceTests {

	@Test
	void updateProfilePersistsColorsAndLegacyUpdatesDoNotResetThem() {
		UserRepository repository = mock(UserRepository.class);
		UserProfileService service = new UserProfileService(repository, mock(PasswordHashingService.class));
		User user = new User("user@example.com", "hash", "Ari", "Rivera", "Ari Rivera", "aririvera");
		ReflectionTestUtils.setField(user, "id", 42L);
		when(repository.findById(42L)).thenReturn(Optional.of(user));
		when(repository.saveAndFlush(user)).thenReturn(user);

		User updated = service.updateAuthenticatedUserProfile(
				42L, "Ari", "Rivera", "Ari Rivera", "@aririvera", null, "#AA7733", "#1122FF");
		assertThat(updated.getAvatarGradientStart()).isEqualTo("#aa7733");
		assertThat(updated.getAvatarGradientEnd()).isEqualTo("#1122ff");

		service.updateAuthenticatedUserProfile(42L, "Ari", "Rivera", "Ari", "@aririvera", "Updated description");
		assertThat(user.getAvatarGradientStart()).isEqualTo("#aa7733");
		assertThat(user.getAvatarGradientEnd()).isEqualTo("#1122ff");
	}

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
	void updateAuthenticatedUserProfileTrimsNormalizesAndPersistsFields() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);
		User user = new User(
				"user@example.com", "hashed-password", "Profile", "User", "Profile User", "profile_user");
		ReflectionTestUtils.setField(user, "id", 42L);

		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(userRepository.saveAndFlush(user)).thenReturn(user);

		User updatedUser = userProfileService.updateAuthenticatedUserProfile(
				42L,
				" Updated ",
				" Name ",
				" Updated Profile ",
				" @Updated_Profile ",
				"  Scenes that move with the music.  ");

		assertThat(updatedUser.getFirstName()).isEqualTo("Updated");
		assertThat(updatedUser.getLastName()).isEqualTo("Name");
		assertThat(updatedUser.getDisplayName()).isEqualTo("Updated Profile");
		assertThat(updatedUser.getHandle()).isEqualTo("updated_profile");
		assertThat(updatedUser.getDescription()).isEqualTo("Scenes that move with the music.");
		verify(userRepository).findById(42L);
		verify(userRepository).saveAndFlush(user);
	}

	@Test
	void updateAuthenticatedUserProfileAllowsKeepingTheSameHandle() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);
		User user = new User(
				"user@example.com", "hashed-password", "Profile", "User", "Profile User", "profile_user");
		ReflectionTestUtils.setField(user, "id", 42L);

		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(userRepository.findByHandle("profile_user")).thenReturn(Optional.of(user));
		when(userRepository.saveAndFlush(user)).thenReturn(user);

		User updatedUser = userProfileService.updateAuthenticatedUserProfile(
				42L, "Profile", "User", "Profile User", "@Profile_User", " ");

		assertThat(updatedUser.getHandle()).isEqualTo("profile_user");
		assertThat(updatedUser.getDescription()).isNull();
		verify(userRepository).saveAndFlush(user);
	}

	@Test
	void updateAuthenticatedUserProfileRejectsAnotherUsersHandle() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);
		User user = new User(
				"user@example.com", "hashed-password", "Profile", "User", "Profile User", "profile_user");
		User owner = new User(
				"owner@example.com", "hashed-password", "Owner", "User", "Owner", "taken_handle");
		ReflectionTestUtils.setField(user, "id", 42L);
		ReflectionTestUtils.setField(owner, "id", 84L);

		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(userRepository.findByHandle("taken_handle")).thenReturn(Optional.of(owner));

		assertThatThrownBy(() -> userProfileService.updateAuthenticatedUserProfile(
				42L, "Profile", "User", "Profile User", "@Taken_Handle", null))
				.isInstanceOf(HandleAlreadyInUseException.class)
				.hasMessage("That handle is already in use.");
	}

	@Test
	void getPublicProfileNormalizesHandleAndReturnsMatchingUser() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);
		User user = new User(
				"user@example.com", "hashed-password", "Profile", "User", "Profile User", "profile_user");

		when(userRepository.findByHandle("profile_user")).thenReturn(Optional.of(user));

		assertThat(userProfileService.getPublicProfile("@Profile_User")).isSameAs(user);
		verify(userRepository).findByHandle("profile_user");
	}

	@Test
	void getPublicProfileReturnsNotFoundForUnknownOrInvalidHandle() {
		UserRepository userRepository = mock(UserRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		UserProfileService userProfileService = new UserProfileService(userRepository, passwordHashingService);

		when(userRepository.findByHandle("missing_user")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userProfileService.getPublicProfile("@missing_user"))
				.isInstanceOf(ProfileNotFoundException.class)
				.hasMessage("Profile not found.");
		assertThatThrownBy(() -> userProfileService.getPublicProfile("not valid"))
				.isInstanceOf(ProfileNotFoundException.class)
				.hasMessage("Profile not found.");
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
