package com.bdmage.mage_backend.service;

import java.time.Instant;
import java.util.Optional;

import com.bdmage.mage_backend.exception.InvalidPasswordResetTokenException;
import com.bdmage.mage_backend.model.PasswordResetToken;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.PasswordResetTokenRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTests {

	// Helper so each test gets a fresh service with its own mocks
	private static PasswordResetService service(
			PasswordResetTokenRepository tokenRepo,
			UserRepository userRepo,
			PasswordHashingService hashingService) {
		return new PasswordResetService(tokenRepo, userRepo, hashingService);
	}

	// Convenience: build a local user and give it a fake DB-assigned id
	private static User localUserWithId(Long id) {
		User user = new User("user@example.com", "hashed-pw", "First", "Last", "Alice");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	// --- requestReset ---

	@Test
	void requestResetIssuesTokenForLocalUser() {
		PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
		UserRepository userRepo = mock(UserRepository.class);
		PasswordHashingService hashingService = mock(PasswordHashingService.class);
		PasswordResetService svc = service(tokenRepo, userRepo, hashingService);

		User localUser = localUserWithId(1L);
		when(userRepo.findByEmail("user@example.com")).thenReturn(Optional.of(localUser));
		when(tokenRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		String rawToken = svc.requestReset("user@example.com");

		// Should hand back a non-blank token
		assertThat(rawToken).isNotBlank();

		// Old tokens for this user should be wiped first
		verify(tokenRepo).deleteAllByUserId(1L);

		ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(tokenRepo).saveAndFlush(captor.capture());

		PasswordResetToken saved = captor.getValue();
		assertThat(saved.getTokenHash()).isEqualTo(PasswordResetService.hashToken(rawToken));
		assertThat(saved.isUsed()).isFalse();
		assertThat(saved.isExpired()).isFalse();
	}

	@Test
	void requestResetNormalizesEmailBeforeLookup() {
		PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
		UserRepository userRepo = mock(UserRepository.class);
		PasswordHashingService hashingService = mock(PasswordHashingService.class);
		PasswordResetService svc = service(tokenRepo, userRepo, hashingService);

		User localUser = localUserWithId(1L);
		when(userRepo.findByEmail("user@example.com")).thenReturn(Optional.of(localUser));
		when(tokenRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		// Mixed case and surrounding whitespace should still work
		String rawToken = svc.requestReset("  USER@EXAMPLE.COM  ");

		assertThat(rawToken).isNotBlank();
		verify(userRepo).findByEmail("user@example.com");
	}

	@Test
	void requestResetReturnsNullWhenEmailNotFound() {
		PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
		UserRepository userRepo = mock(UserRepository.class);
		PasswordHashingService hashingService = mock(PasswordHashingService.class);
		PasswordResetService svc = service(tokenRepo, userRepo, hashingService);

		when(userRepo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

		// Null signals "no action taken" — caller still returns 200 to avoid leaking info
		String result = svc.requestReset("ghost@example.com");

		assertThat(result).isNull();
		verify(tokenRepo, never()).saveAndFlush(any());
	}

	@Test
	void requestResetReturnsNullForGoogleOnlyAccount() {
		PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
		UserRepository userRepo = mock(UserRepository.class);
		PasswordHashingService hashingService = mock(PasswordHashingService.class);
		PasswordResetService svc = service(tokenRepo, userRepo, hashingService);

		// Google-only user — no local password to reset
		User googleUser = User.google("google@example.com", "google-subject-abc", "First", "Last", "Bob");
		when(userRepo.findByEmail("google@example.com")).thenReturn(Optional.of(googleUser));

		String result = svc.requestReset("google@example.com");

		assertThat(result).isNull();
		verify(tokenRepo, never()).saveAndFlush(any());
	}

	// --- confirmReset ---

	@Test
	void confirmResetUpdatesPasswordAndMarksTokenUsed() {
		PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
		UserRepository userRepo = mock(UserRepository.class);
		PasswordHashingService hashingService = mock(PasswordHashingService.class);
		PasswordResetService svc = service(tokenRepo, userRepo, hashingService);

		String rawToken = "some-raw-token";
		String tokenHash = PasswordResetService.hashToken(rawToken);

		// Valid token — not used, not expired
		PasswordResetToken validToken = new PasswordResetToken(1L, tokenHash, Instant.now().plusSeconds(600));

		User localUser = new User("user@example.com", "old-hashed-pw", "First", "Last", "Alice");
		when(tokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(validToken));
		when(userRepo.findById(1L)).thenReturn(Optional.of(localUser));
		when(hashingService.hash("new-password")).thenReturn("new-hashed-pw");
		when(tokenRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
		when(userRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

		svc.confirmReset(rawToken, "new-password");

		// Token should be marked used so it can't be replayed
		assertThat(validToken.isUsed()).isTrue();
		verify(tokenRepo).saveAndFlush(validToken);

		// Password should be updated
		assertThat(localUser.getPasswordHash()).isEqualTo("new-hashed-pw");
		verify(userRepo).saveAndFlush(localUser);
	}

	@Test
	void confirmResetThrowsForUnknownToken() {
		PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
		UserRepository userRepo = mock(UserRepository.class);
		PasswordHashingService hashingService = mock(PasswordHashingService.class);
		PasswordResetService svc = service(tokenRepo, userRepo, hashingService);

		when(tokenRepo.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> svc.confirmReset("made-up-token", "new-password"))
				.isInstanceOf(InvalidPasswordResetTokenException.class);
	}

	@Test
	void confirmResetThrowsForAlreadyUsedToken() {
		PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
		UserRepository userRepo = mock(UserRepository.class);
		PasswordHashingService hashingService = mock(PasswordHashingService.class);
		PasswordResetService svc = service(tokenRepo, userRepo, hashingService);

		String rawToken = "used-token";
		String tokenHash = PasswordResetService.hashToken(rawToken);

		PasswordResetToken usedToken = new PasswordResetToken(1L, tokenHash, Instant.now().plusSeconds(600));
		usedToken.markUsed();

		when(tokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(usedToken));

		assertThatThrownBy(() -> svc.confirmReset(rawToken, "new-password"))
				.isInstanceOf(InvalidPasswordResetTokenException.class);

		verify(userRepo, never()).saveAndFlush(any());
	}

	@Test
	void confirmResetThrowsForExpiredToken() {
		PasswordResetTokenRepository tokenRepo = mock(PasswordResetTokenRepository.class);
		UserRepository userRepo = mock(UserRepository.class);
		PasswordHashingService hashingService = mock(PasswordHashingService.class);
		PasswordResetService svc = service(tokenRepo, userRepo, hashingService);

		String rawToken = "expired-token";
		String tokenHash = PasswordResetService.hashToken(rawToken);

		// Token expired in the past
		PasswordResetToken expiredToken = new PasswordResetToken(1L, tokenHash, Instant.now().minusSeconds(60));

		when(tokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredToken));

		assertThatThrownBy(() -> svc.confirmReset(rawToken, "new-password"))
				.isInstanceOf(InvalidPasswordResetTokenException.class);

		verify(userRepo, never()).saveAndFlush(any());
	}

}
