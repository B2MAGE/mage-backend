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

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected User() {
	}

	public User(String email, String passwordHash, String displayName) {
		this(email, passwordHash, displayName, "", displayName);
	}

	public User(String email, String passwordHash, String firstName, String lastName, String displayName) {
		this(email, AuthProvider.LOCAL, passwordHash, null, firstName, lastName, displayName);
	}

	public static User google(String email, String googleSubject, String displayName) {
		return google(email, googleSubject, displayName, "", displayName);
	}

	public static User google(String email, String googleSubject, String firstName, String lastName, String displayName) {
		return new User(email, AuthProvider.GOOGLE, null, googleSubject, firstName, lastName, displayName);
	}

	public static User localAndGoogle(String email, String passwordHash, String googleSubject, String displayName) {
		return localAndGoogle(email, passwordHash, googleSubject, displayName, "", displayName);
	}

	public static User localAndGoogle(
			String email,
			String passwordHash,
			String googleSubject,
			String firstName,
			String lastName,
			String displayName) {
		return new User(email, AuthProvider.LOCAL_GOOGLE, passwordHash, googleSubject, firstName, lastName, displayName);
	}

	private User(
			String email,
			AuthProvider authProvider,
			String passwordHash,
			String googleSubject,
			String firstName,
			String lastName,
			String displayName) {
		this.email = Objects.requireNonNull(email, "email must not be null");
		this.authProvider = Objects.requireNonNull(authProvider, "authProvider must not be null");
		this.passwordHash = passwordHash;
		this.googleSubject = googleSubject;
		this.firstName = Objects.requireNonNull(firstName, "firstName must not be null");
		this.lastName = Objects.requireNonNull(lastName, "lastName must not be null");
		this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
		validateProviderFields(authProvider, passwordHash, googleSubject);
	}

	private static void validateProviderFields(AuthProvider authProvider, String passwordHash, String googleSubject) {
		switch (authProvider) {
			case LOCAL -> {
				if (passwordHash == null) {
					throw new IllegalArgumentException("Local users require a password hash");
				}
				if (googleSubject != null) {
					throw new IllegalArgumentException("Local users cannot have a Google subject");
				}
			}
			case GOOGLE -> {
				if (passwordHash != null) {
					throw new IllegalArgumentException("Google users cannot have a local password hash");
				}
				if (googleSubject == null) {
					throw new IllegalArgumentException("Google users require a Google subject");
				}
			}
			case LOCAL_GOOGLE -> {
				if (passwordHash == null) {
					throw new IllegalArgumentException("Linked users require a password hash");
				}
				if (googleSubject == null) {
					throw new IllegalArgumentException("Linked users require a Google subject");
				}
			}
		}
	}

	public boolean supportsLocalAuthentication() {
		return this.passwordHash != null;
	}

	public boolean supportsGoogleAuthentication() {
		return this.googleSubject != null;
	}

	public void linkGoogle(String googleSubject) {
		this.googleSubject = Objects.requireNonNull(googleSubject, "googleSubject must not be null");
		syncAuthProvider();
	}

	public void linkLocalPassword(String passwordHash) {
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
		syncAuthProvider();
	}

	public void updateLocalPassword(String passwordHash) {
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
		syncAuthProvider();
	}

	public void updateProfileName(String firstName, String lastName, String displayName) {
		this.firstName = Objects.requireNonNull(firstName, "firstName must not be null");
		this.lastName = Objects.requireNonNull(lastName, "lastName must not be null");
		this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
	}

	private void syncAuthProvider() {
		if (this.passwordHash != null && this.googleSubject != null) {
			this.authProvider = AuthProvider.LOCAL_GOOGLE;
			return;
		}

		if (this.passwordHash != null) {
			this.authProvider = AuthProvider.LOCAL;
			return;
		}

		if (this.googleSubject != null) {
			this.authProvider = AuthProvider.GOOGLE;
			return;
		}

		throw new IllegalStateException("User must support at least one authentication provider");
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

	public String getFirstName() {
		return this.firstName;
	}

	public String getLastName() {
		return this.lastName;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}
}
