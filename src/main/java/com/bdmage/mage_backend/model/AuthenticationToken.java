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
@Table(name = "auth_tokens")
public class AuthenticationToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected AuthenticationToken() {
	}

	public AuthenticationToken(Long userId, String tokenHash) {
		this.userId = Objects.requireNonNull(userId, "userId must not be null");
		this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
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

	public Instant getCreatedAt() {
		return this.createdAt;
	}
}
