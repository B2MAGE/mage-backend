package com.bdmage.mage_backend.repository;

import java.util.Optional;

import com.bdmage.mage_backend.model.AuthenticationToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationTokenRepository extends JpaRepository<AuthenticationToken, Long> {

	Optional<AuthenticationToken> findByTokenHash(String tokenHash);
}
