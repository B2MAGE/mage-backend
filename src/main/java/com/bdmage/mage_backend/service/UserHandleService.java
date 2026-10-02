package com.bdmage.mage_backend.service;

import java.util.Locale;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.exception.HandleAlreadyInUseException;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserHandleService {

	public static final String HANDLE_PATTERN = "^[a-z][a-z0-9_]{2,29}$";
	private static final Pattern VALID_HANDLE = Pattern.compile(HANDLE_PATTERN);
	private static final String HANDLE_ALREADY_IN_USE_MESSAGE = "That handle is already in use.";

	private final UserRepository userRepository;

	public UserHandleService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public String normalizeInput(String handle) {
		String normalized = handle.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("@")) {
			normalized = normalized.substring(1);
		}
		if (!VALID_HANDLE.matcher(normalized).matches()) {
			throw new IllegalArgumentException("Handle format is invalid.");
		}
		return normalized;
	}

	public void requireAvailable(String handle, Long excludedUserId) {
		this.userRepository.findByHandle(handle)
				.filter(user -> excludedUserId == null || !user.getId().equals(excludedUserId))
				.ifPresent(user -> {
					throw new HandleAlreadyInUseException(HANDLE_ALREADY_IN_USE_MESSAGE);
				});
	}

	public static HandleAlreadyInUseException conflict() {
		return new HandleAlreadyInUseException(HANDLE_ALREADY_IN_USE_MESSAGE);
	}
}
