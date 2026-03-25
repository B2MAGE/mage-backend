package com.bdmage.mage_backend.service;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";

	private final UserRepository userRepository;

	public UserProfileService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public User getAuthenticatedUser(Long authenticatedUserId) {
		if (authenticatedUserId == null) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}

		return this.userRepository.findById(authenticatedUserId)
				.orElseThrow(() -> new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE));
	}
}
