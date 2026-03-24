package com.bdmage.mage_backend.service;

import java.util.Locale;

import com.bdmage.mage_backend.exception.EmailAlreadyRegisteredException;
import com.bdmage.mage_backend.model.AuthProvider;
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
	public User register(String email, String plainPassword, String displayName) {
		String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
		String trimmedDisplayName = displayName.trim();

		if (isEmailAlreadyRegistered(normalisedEmail)) {
			throw new EmailAlreadyRegisteredException(
					"An account with this email address is already registered.");
		}

		String passwordHash = this.passwordHashingService.hash(plainPassword);
		User newUser = new User(normalisedEmail, passwordHash, trimmedDisplayName);
		return this.userRepository.saveAndFlush(newUser);
	}

	private boolean isEmailAlreadyRegistered(String normalisedEmail) {
		return this.userRepository.findByEmailAndAuthProvider(normalisedEmail, AuthProvider.LOCAL).isPresent()
				|| this.userRepository.findByEmailAndAuthProvider(normalisedEmail, AuthProvider.GOOGLE).isPresent();
	}
}
