package com.bdmage.mage_backend.service;

import java.util.Locale;

import com.bdmage.mage_backend.exception.InvalidCredentialsException;
import com.bdmage.mage_backend.model.AuthProvider;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

	private static final String INVALID_CREDENTIALS_MESSAGE = "Email or password is incorrect.";

	private final UserRepository userRepository;
	private final PasswordHashingService passwordHashingService;

	public LoginService(
			UserRepository userRepository,
			PasswordHashingService passwordHashingService) {
		this.userRepository = userRepository;
		this.passwordHashingService = passwordHashingService;
	}

	@Transactional(readOnly = true)
	public User login(String email, String plainPassword) {
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

		User localUser = this.userRepository.findByEmailAndAuthProvider(normalizedEmail, AuthProvider.LOCAL)
				.orElseThrow(() -> new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE));

		String passwordHash = localUser.getPasswordHash();
		if (passwordHash == null || !this.passwordHashingService.matches(plainPassword, passwordHash)) {
			throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
		}

		return localUser;
	}
}
