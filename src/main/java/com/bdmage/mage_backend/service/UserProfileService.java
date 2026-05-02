package com.bdmage.mage_backend.service;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidCurrentPasswordException;
import com.bdmage.mage_backend.exception.LocalPasswordChangeUnavailableException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String INVALID_CURRENT_PASSWORD_MESSAGE = "Current password is incorrect.";
	private static final String LOCAL_PASSWORD_CHANGE_UNAVAILABLE_MESSAGE =
			"Local password changes are not available for this account.";

	private final UserRepository userRepository;
	private final PasswordHashingService passwordHashingService;

	public UserProfileService(
			UserRepository userRepository,
			PasswordHashingService passwordHashingService) {
		this.userRepository = userRepository;
		this.passwordHashingService = passwordHashingService;
	}

	@Transactional(readOnly = true)
	public User getAuthenticatedUser(Long authenticatedUserId) {
		if (authenticatedUserId == null) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}

		return this.userRepository.findById(authenticatedUserId)
				.orElseThrow(() -> new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE));
	}

	@Transactional
	public User updateAuthenticatedUserProfile(
			Long authenticatedUserId,
			String firstName,
			String lastName,
			String displayName) {
		User user = getAuthenticatedUser(authenticatedUserId);
		user.updateProfileName(firstName.trim(), lastName.trim(), displayName.trim());
		return this.userRepository.saveAndFlush(user);
	}

	@Transactional
	public void changeAuthenticatedUserPassword(
			Long authenticatedUserId,
			String currentPassword,
			String newPassword) {
		User user = getAuthenticatedUser(authenticatedUserId);

		if (!user.supportsLocalAuthentication() || user.getPasswordHash() == null) {
			throw new LocalPasswordChangeUnavailableException(LOCAL_PASSWORD_CHANGE_UNAVAILABLE_MESSAGE);
		}

		if (!this.passwordHashingService.matches(currentPassword, user.getPasswordHash())) {
			throw new InvalidCurrentPasswordException(INVALID_CURRENT_PASSWORD_MESSAGE);
		}

		user.updateLocalPassword(this.passwordHashingService.hash(newPassword));
		this.userRepository.saveAndFlush(user);
	}
}
