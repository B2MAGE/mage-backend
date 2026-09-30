package com.bdmage.mage_backend.service;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidCurrentPasswordException;
import com.bdmage.mage_backend.exception.LocalPasswordChangeUnavailableException;
import com.bdmage.mage_backend.exception.ProfileNotFoundException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserProfileService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String INVALID_CURRENT_PASSWORD_MESSAGE = "Current password is incorrect.";
	private static final String LOCAL_PASSWORD_CHANGE_UNAVAILABLE_MESSAGE =
			"Local password changes are not available for this account.";
	private static final String PROFILE_NOT_FOUND_MESSAGE = "Profile not found.";

	private final UserRepository userRepository;
	private final PasswordHashingService passwordHashingService;
	private final UserHandleService userHandleService;

	public UserProfileService(
			UserRepository userRepository,
			PasswordHashingService passwordHashingService) {
		this(userRepository, passwordHashingService, new UserHandleService(userRepository));
	}

	@Autowired
	public UserProfileService(
			UserRepository userRepository,
			PasswordHashingService passwordHashingService,
			UserHandleService userHandleService) {
		this.userRepository = userRepository;
		this.passwordHashingService = passwordHashingService;
		this.userHandleService = userHandleService;
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
			String displayName,
			String handle,
			String description) {
		User user = getAuthenticatedUser(authenticatedUserId);
		String normalizedHandle = this.userHandleService.normalizeInput(handle);
		this.userHandleService.requireAvailable(normalizedHandle, user.getId());
		user.updateProfile(
				firstName.trim(),
				lastName.trim(),
				displayName.trim(),
				normalizedHandle,
				normalizeOptionalText(description));
		try {
			return this.userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException ex) {
			throw UserHandleService.conflict();
		}
	}

	@Transactional(readOnly = true)
	public User getPublicProfile(String handle) {
		try {
			String normalizedHandle = this.userHandleService.normalizeInput(handle);
			return this.userRepository.findByHandle(normalizedHandle)
					.orElseThrow(() -> new ProfileNotFoundException(PROFILE_NOT_FOUND_MESSAGE));
		} catch (IllegalArgumentException ex) {
			throw new ProfileNotFoundException(PROFILE_NOT_FOUND_MESSAGE);
		}
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

	private static String normalizeOptionalText(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
