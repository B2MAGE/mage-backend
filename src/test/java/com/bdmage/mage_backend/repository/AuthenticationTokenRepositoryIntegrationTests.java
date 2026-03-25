package com.bdmage.mage_backend.repository;

import com.bdmage.mage_backend.model.AuthenticationToken;
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
class AuthenticationTokenRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private AuthenticationTokenRepository authenticationTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savePersistsTokenHashAndSupportsLookupByTokenHash() {
		User user = this.userRepository.saveAndFlush(new User(
				"token-user-" + System.nanoTime() + "@example.com",
				"hashed-password",
				"Token User"));
		AuthenticationToken savedToken = this.authenticationTokenRepository.saveAndFlush(new AuthenticationToken(
				user.getId(),
				"c".repeat(64)));

		this.entityManager.clear();

		AuthenticationToken foundToken = this.authenticationTokenRepository.findByTokenHash("c".repeat(64)).orElseThrow();

		assertThat(savedToken.getId()).isNotNull();
		assertThat(foundToken.getId()).isEqualTo(savedToken.getId());
		assertThat(foundToken.getUserId()).isEqualTo(user.getId());
		assertThat(foundToken.getCreatedAt()).isNotNull();
	}
}
