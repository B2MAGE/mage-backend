package com.bdmage.mage_backend.model;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "email", nullable = false, length = 320)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(name = "auth_provider", nullable = false, length = 20)
	private AuthProvider authProvider;

	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Column(name = "google_subject", length = 255)
	private String googleSubject;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected User() {
	}

	public User(String email, String passwordHash, String displayName) {
		this(email, AuthProvider.LOCAL, passwordHash, null, displayName);
	}

	public static User google(String email, String googleSubject, String displayName) {
		return new User(email, AuthProvider.GOOGLE, null, googleSubject, displayName);
	}

	private User(String email, AuthProvider authProvider, String passwordHash, String googleSubject, String displayName) {
		this.email = Objects.requireNonNull(email, "email must not be null");
		this.authProvider = Objects.requireNonNull(authProvider, "authProvider must not be null");
		this.passwordHash = passwordHash;
		this.googleSubject = googleSubject;
		this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
		validateProviderFields(authProvider, passwordHash, googleSubject);
	}

	private static void validateProviderFields(AuthProvider authProvider, String passwordHash, String googleSubject) {
		if (authProvider == AuthProvider.LOCAL && passwordHash == null) {
			throw new IllegalArgumentException("Local users require a password hash");
		}

		if (authProvider == AuthProvider.LOCAL && googleSubject != null) {
			throw new IllegalArgumentException("Local users cannot have a Google subject");
		}

		if (authProvider == AuthProvider.GOOGLE && passwordHash != null) {
			throw new IllegalArgumentException("Google users cannot have a local password hash");
		}

		if (authProvider == AuthProvider.GOOGLE && googleSubject == null) {
			throw new IllegalArgumentException("Google users require a Google subject");
		}
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public AuthProvider getAuthProvider() {
		return this.authProvider;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public String getGoogleSubject() {
		return this.googleSubject;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}
}
