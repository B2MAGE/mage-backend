package com.bdmage.mage_backend.service;

import java.util.Locale;
import java.util.Optional;

import com.bdmage.mage_backend.exception.AccountLinkRequiredException;
import com.bdmage.mage_backend.exception.EmailAlreadyRegisteredException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	private final UserRepository userRepository;
	private final PasswordHashingService passwordHashingService;

	public RegistrationService(
			UserRepository userRepository,
			PasswordHashingService passwordHashingService) {
		this.userRepository = userRepository;
		this.passwordHashingService = passwordHashingService;
	}

	@Transactional
	public User register(String email, String plainPassword, String firstName, String lastName, String displayName) {
		String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
		String trimmedFirstName = firstName.trim();
		String trimmedLastName = lastName.trim();
		String trimmedDisplayName = displayName.trim();

		Optional<User> existingUser = this.userRepository.findByEmail(normalisedEmail);
		if (existingUser.isPresent()) {
			if (existingUser.get().supportsLocalAuthentication()) {
				throw new EmailAlreadyRegisteredException("Local authentication is already configured for this email.");
			}

			throw new AccountLinkRequiredException(
					"A Google-backed account already exists for this email. Link local authentication through /api/auth/link/local after authenticating with Google.");
		}

		String passwordHash = this.passwordHashingService.hash(plainPassword);
		User newUser = new User(
				normalisedEmail,
				passwordHash,
				trimmedFirstName,
				trimmedLastName,
				trimmedDisplayName);
		return this.userRepository.saveAndFlush(newUser);
	}
}
