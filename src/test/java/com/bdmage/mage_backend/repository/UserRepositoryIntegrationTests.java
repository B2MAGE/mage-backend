package com.bdmage.mage_backend.repository;

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
	void savePersistsUserAndFindByEmailReturnsStoredRecord() {
		String email = "user-" + System.nanoTime() + "@example.com";
		User savedUser = userRepository.saveAndFlush(new User(email, "hashed-password-value", "Test User"));

		entityManager.clear();

		User foundUser = userRepository.findByEmail(email).orElseThrow();

		assertThat(savedUser.getId()).isNotNull();
		assertThat(foundUser.getId()).isEqualTo(savedUser.getId());
		assertThat(foundUser.getEmail()).isEqualTo(email);
		assertThat(foundUser.getPasswordHash()).isEqualTo("hashed-password-value");
		assertThat(foundUser.getDisplayName()).isEqualTo("Test User");
		assertThat(foundUser.getCreatedAt()).isNotNull();
	}

	@Test
	void findByEmailReturnsEmptyWhenNoUserMatches() {
		String missingEmail = "missing-" + System.nanoTime() + "@example.com";

		assertThat(userRepository.findByEmail(missingEmail)).isEmpty();
	}
}
