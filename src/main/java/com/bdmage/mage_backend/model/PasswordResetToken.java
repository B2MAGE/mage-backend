package com.bdmage.mage_backend.model;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	// We only store the hash — the raw token lives only in the response
	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	// Flipped to true once the token has been used to reset a password
	@Column(name = "used", nullable = false)
	private boolean used;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected PasswordResetToken() {
	}

	public PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
		this.userId = Objects.requireNonNull(userId, "userId must not be null");
		this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
		this.used = false;
	}

	public boolean isExpired() {
		return Instant.now().isAfter(this.expiresAt);
	}

	public void markUsed() {
		this.used = true;
	}

	public Long getId() {
		return this.id;
	}

	public Long getUserId() {
		return this.userId;
	}

	public String getTokenHash() {
		return this.tokenHash;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public boolean isUsed() {
		return this.used;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
