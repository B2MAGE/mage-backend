package com.bdmage.mage_backend.repository;

import java.util.Optional;

import com.bdmage.mage_backend.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	// Look up a token by its SHA-256 hash — we never store or query by raw value
	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	// Wipe any existing reset tokens for a user before issuing a new one
	// so old links immediately stop working
	void deleteAllByUserId(Long userId);

}
