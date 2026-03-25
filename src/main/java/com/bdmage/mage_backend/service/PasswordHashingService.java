package com.bdmage.mage_backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * PasswordHashingService
 *
 * Centralises all password hashing and verification logic so that no other
 * class needs to know which algorithm or configuration is in use.
 *
 * Uses BCrypt, which is the industry-standard adaptive hashing algorithm for
 * passwords. BCrypt automatically generates and embeds a random salt into
 * every hash, so two calls with the same plain-text password will produce
 * different hashes that still verify correctly.
 */
@Service
public class PasswordHashingService {
	private final PasswordEncoder passwordEncoder;

	public PasswordHashingService(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

    /**
     * hash(plainPassword)
     *
     * Converts a plain-text password into a BCrypt hash that is safe to
     * store in the database.
     *
     * The raw password is never stored or returned anywhere in the system.
     *
     * @param plainPassword the raw password supplied by the user
     * @return a BCrypt hash string suitable for persistence
     */
	public String hash(String plainPassword) {
		return this.passwordEncoder.encode(plainPassword);
	}

    /**
     * matches(plainPassword, hash)
     *
     * Verifies that a plain-text password matches a previously stored hash.
     *
     * Used during login to validate the user's credentials without ever
     * needing to store or compare raw passwords.
     *
     * @param plainPassword the raw password to verify
     * @param hash          the stored BCrypt hash to compare against
     * @return true if the password matches the hash, false otherwise
     */
	public boolean matches(String plainPassword, String hash) {
		return this.passwordEncoder.matches(plainPassword, hash);
	}
}
