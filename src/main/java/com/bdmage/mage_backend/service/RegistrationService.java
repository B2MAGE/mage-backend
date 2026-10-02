package com.bdmage.mage_backend.service;

import java.util.Locale;
import java.util.Optional;

import com.bdmage.mage_backend.exception.AccountLinkRequiredException;
import com.bdmage.mage_backend.exception.EmailAlreadyRegisteredException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	private static final String USERS_EMAIL_CONSTRAINT = "users_email_key";
	private static final String USERS_HANDLE_CONSTRAINT = "users_handle_key";

	private final UserRepository userRepository;
	private final PasswordHashingService passwordHashingService;
	private final UserHandleService userHandleService;

	public RegistrationService(
			UserRepository userRepository,
			PasswordHashingService passwordHashingService) {
		this(userRepository, passwordHashingService, new UserHandleService(userRepository));
	}

	@Autowired
	public RegistrationService(
			UserRepository userRepository,
			PasswordHashingService passwordHashingService,
			UserHandleService userHandleService) {
		this.userRepository = userRepository;
		this.passwordHashingService = passwordHashingService;
		this.userHandleService = userHandleService;
	}

	@Transactional
	public User register(
			String email,
			String plainPassword,
			String firstName,
			String lastName,
			String displayName,
			String handle) {
		String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
		String trimmedFirstName = firstName.trim();
		String trimmedLastName = lastName.trim();
		String trimmedDisplayName = displayName.trim();
		String normalizedHandle = this.userHandleService.normalizeInput(handle);

		Optional<User> existingUser = this.userRepository.findByEmail(normalisedEmail);
		if (existingUser.isPresent()) {
			if (existingUser.get().supportsLocalAuthentication()) {
				throw new EmailAlreadyRegisteredException("Local authentication is already configured for this email.");
			}

			throw new AccountLinkRequiredException(
					"A Google-backed account already exists for this email. Link local authentication through /api/auth/link/local after authenticating with Google.");
		}

		this.userHandleService.requireAvailable(normalizedHandle, null);
		String passwordHash = this.passwordHashingService.hash(plainPassword);
		User newUser = new User(
				normalisedEmail,
				passwordHash,
				trimmedFirstName,
				trimmedLastName,
				trimmedDisplayName,
				normalizedHandle);
		try {
			return this.userRepository.saveAndFlush(newUser);
		} catch (DataIntegrityViolationException ex) {
			if (violatesConstraint(ex, USERS_EMAIL_CONSTRAINT)) {
				throw new EmailAlreadyRegisteredException("An account is already registered for this email.");
			}
			if (violatesConstraint(ex, USERS_HANDLE_CONSTRAINT)) {
				throw UserHandleService.conflict();
			}
			throw ex;
		}
	}

	private static boolean violatesConstraint(Throwable exception, String constraintName) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolation
					&& constraintName.equalsIgnoreCase(constraintViolation.getConstraintName())) {
				return true;
			}

			String message = current.getMessage();
			if (message != null && message.toLowerCase(Locale.ROOT).contains(constraintName)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
