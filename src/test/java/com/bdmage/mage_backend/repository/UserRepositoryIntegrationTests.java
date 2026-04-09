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
	void savePersistsLocalGoogleAndLinkedUsersAndSupportsEmailAndSubjectLookups() {
		String localEmail = "local-user-" + System.nanoTime() + "@example.com";
		String googleEmail = "google-user-" + System.nanoTime() + "@example.com";
		String linkedEmail = "linked-user-" + System.nanoTime() + "@example.com";
		String googleSubject = "google-subject-" + System.nanoTime();
		String linkedGoogleSubject = "linked-google-subject-" + System.nanoTime();
		User savedLocalUser = userRepository.saveAndFlush(new User(localEmail, "hashed-password-value", "Local User"));
		User savedGoogleUser = userRepository.saveAndFlush(User.google(googleEmail, googleSubject, "Google User"));
		User savedLinkedUser = userRepository.saveAndFlush(User.localAndGoogle(
				linkedEmail,
				"linked-password-hash",
				linkedGoogleSubject,
				"Linked User"));

		entityManager.clear();

		User foundLocalUser = userRepository.findByEmail(localEmail).orElseThrow();
		User foundGoogleUserByEmail = userRepository.findByEmail(googleEmail).orElseThrow();
		User foundGoogleUserBySubject = userRepository.findByGoogleSubject(googleSubject).orElseThrow();
		User foundLinkedUserByEmail = userRepository.findByEmail(linkedEmail).orElseThrow();
		User foundLinkedUserBySubject = userRepository.findByGoogleSubject(linkedGoogleSubject).orElseThrow();

		assertThat(savedLocalUser.getId()).isNotNull();
		assertThat(savedGoogleUser.getId()).isNotNull();
		assertThat(savedLinkedUser.getId()).isNotNull();

		assertThat(foundLocalUser.getId()).isEqualTo(savedLocalUser.getId());
		assertThat(foundLocalUser.getEmail()).isEqualTo(localEmail);
		assertThat(foundLocalUser.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
		assertThat(foundLocalUser.getPasswordHash()).isEqualTo("hashed-password-value");
		assertThat(foundLocalUser.getGoogleSubject()).isNull();
		assertThat(foundLocalUser.getDisplayName()).isEqualTo("Local User");
		assertThat(foundLocalUser.getCreatedAt()).isNotNull();

		assertThat(foundGoogleUserByEmail.getId()).isEqualTo(savedGoogleUser.getId());
		assertThat(foundGoogleUserByEmail.getEmail()).isEqualTo(googleEmail);
		assertThat(foundGoogleUserByEmail.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
		assertThat(foundGoogleUserByEmail.getPasswordHash()).isNull();
		assertThat(foundGoogleUserByEmail.getGoogleSubject()).isEqualTo(googleSubject);
		assertThat(foundGoogleUserByEmail.getDisplayName()).isEqualTo("Google User");
		assertThat(foundGoogleUserByEmail.getCreatedAt()).isNotNull();

		assertThat(foundGoogleUserBySubject.getId()).isEqualTo(savedGoogleUser.getId());
		assertThat(foundLinkedUserByEmail.getId()).isEqualTo(savedLinkedUser.getId());
		assertThat(foundLinkedUserByEmail.getEmail()).isEqualTo(linkedEmail);
		assertThat(foundLinkedUserByEmail.getAuthProvider()).isEqualTo(AuthProvider.LOCAL_GOOGLE);
		assertThat(foundLinkedUserByEmail.getPasswordHash()).isEqualTo("linked-password-hash");
		assertThat(foundLinkedUserByEmail.getGoogleSubject()).isEqualTo(linkedGoogleSubject);
		assertThat(foundLinkedUserByEmail.getDisplayName()).isEqualTo("Linked User");
		assertThat(foundLinkedUserByEmail.getCreatedAt()).isNotNull();
		assertThat(foundLinkedUserBySubject.getId()).isEqualTo(savedLinkedUser.getId());
	}

	@Test
	void emailAndGoogleSubjectLookupsReturnEmptyWhenNoUserMatches() {
		String missingEmail = "missing-" + System.nanoTime() + "@example.com";
		String missingGoogleSubject = "missing-google-subject-" + System.nanoTime();

		assertThat(userRepository.findByEmail(missingEmail)).isEmpty();
		assertThat(userRepository.findByGoogleSubject(missingGoogleSubject)).isEmpty();
	}
}
