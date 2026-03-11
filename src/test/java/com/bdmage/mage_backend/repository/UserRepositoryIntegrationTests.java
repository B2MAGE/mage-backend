package com.bdmage.mage_backend.repository;

import com.bdmage.mage_backend.model.AuthProvider;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private UserRepository userRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savePersistsLocalAndGoogleUsersAndSupportsProviderAwareLookups() {
		String sharedEmail = "user-" + System.nanoTime() + "@example.com";
		String googleSubject = "google-subject-" + System.nanoTime();
		User savedLocalUser = userRepository.saveAndFlush(new User(sharedEmail, "hashed-password-value", "Local User"));
		User savedGoogleUser = userRepository.saveAndFlush(User.google(sharedEmail, googleSubject, "Google User"));

		entityManager.clear();

		User foundLocalUser = userRepository.findByEmailAndAuthProvider(sharedEmail, AuthProvider.LOCAL).orElseThrow();
		User foundGoogleUserByEmail = userRepository.findByEmailAndAuthProvider(sharedEmail, AuthProvider.GOOGLE)
				.orElseThrow();
		User foundGoogleUserBySubject = userRepository.findByGoogleSubject(googleSubject).orElseThrow();

		assertThat(savedLocalUser.getId()).isNotNull();
		assertThat(savedGoogleUser.getId()).isNotNull();

		assertThat(foundLocalUser.getId()).isEqualTo(savedLocalUser.getId());
		assertThat(foundLocalUser.getEmail()).isEqualTo(sharedEmail);
		assertThat(foundLocalUser.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
		assertThat(foundLocalUser.getPasswordHash()).isEqualTo("hashed-password-value");
		assertThat(foundLocalUser.getGoogleSubject()).isNull();
		assertThat(foundLocalUser.getDisplayName()).isEqualTo("Local User");
		assertThat(foundLocalUser.getCreatedAt()).isNotNull();

		assertThat(foundGoogleUserByEmail.getId()).isEqualTo(savedGoogleUser.getId());
		assertThat(foundGoogleUserByEmail.getEmail()).isEqualTo(sharedEmail);
		assertThat(foundGoogleUserByEmail.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(foundGoogleUserByEmail.getPasswordHash()).isNull();
		assertThat(foundGoogleUserByEmail.getGoogleSubject()).isEqualTo(googleSubject);
		assertThat(foundGoogleUserByEmail.getDisplayName()).isEqualTo("Google User");
		assertThat(foundGoogleUserByEmail.getCreatedAt()).isNotNull();

		assertThat(foundGoogleUserBySubject.getId()).isEqualTo(savedGoogleUser.getId());
	}

	@Test
	void providerAwareLookupsReturnEmptyWhenNoUserMatches() {
		String missingEmail = "missing-" + System.nanoTime() + "@example.com";
		String missingGoogleSubject = "missing-google-subject-" + System.nanoTime();

		assertThat(userRepository.findByEmailAndAuthProvider(missingEmail, AuthProvider.LOCAL)).isEmpty();
		assertThat(userRepository.findByEmailAndAuthProvider(missingEmail, AuthProvider.GOOGLE)).isEmpty();
		assertThat(userRepository.findByGoogleSubject(missingGoogleSubject)).isEmpty();
	}
}
