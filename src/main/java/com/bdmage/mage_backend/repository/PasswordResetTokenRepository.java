package com.bdmage.mage_backend.repository;

import java.util.Optional;

import com.bdmage.mage_backend.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	void deleteByUserIdAndUsedAtIsNull(Long userId);
}
