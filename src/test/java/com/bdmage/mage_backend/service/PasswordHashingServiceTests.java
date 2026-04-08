package com.bdmage.mage_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashingServiceTests {

	@Test
	void hashReturnsABcryptHashThatDiffersFromTheRawPassword() {
		PasswordHashingService passwordHashingService = new PasswordHashingService(new BCryptPasswordEncoder());

		String hash = passwordHashingService.hash("plain-password");

		assertThat(hash).isNotBlank();
		assertThat(hash).isNotEqualTo("plain-password");
		assertThat(hash).startsWith("$2");
	}

	@Test
	void hashUsesSaltSoTheSamePasswordProducesDifferentHashes() {
		PasswordHashingService passwordHashingService = new PasswordHashingService(new BCryptPasswordEncoder());

		String firstHash = passwordHashingService.hash("plain-password");
		String secondHash = passwordHashingService.hash("plain-password");

		assertThat(firstHash).isNotEqualTo(secondHash);
	}

	@Test
	void matchesReturnsTrueForAValidPasswordAndFalseForAnInvalidPassword() {
		PasswordHashingService passwordHashingService = new PasswordHashingService(new BCryptPasswordEncoder());

		String hash = passwordHashingService.hash("plain-password");

		assertThat(passwordHashingService.matches("plain-password", hash)).isTrue();
		assertThat(passwordHashingService.matches("wrong-password", hash)).isFalse();
	}
}
