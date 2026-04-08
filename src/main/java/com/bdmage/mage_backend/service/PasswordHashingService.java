package com.bdmage.mage_backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordHashingService {

	private final PasswordEncoder passwordEncoder;

	public PasswordHashingService(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	public String hash(String plainPassword) {
		return this.passwordEncoder.encode(plainPassword);
	}

	public boolean matches(String plainPassword, String hash) {
		return this.passwordEncoder.matches(plainPassword, hash);
	}
}
