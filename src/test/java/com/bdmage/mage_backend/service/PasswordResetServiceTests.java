package com.bdmage.mage_backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.bdmage.mage_backend.config.PasswordResetDeliveryMode;
import com.bdmage.mage_backend.config.PasswordResetProperties;
import com.bdmage.mage_backend.exception.InvalidPasswordResetTokenException;
import com.bdmage.mage_backend.model.PasswordResetToken;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.AuthenticationTokenRepository;
import com.bdmage.mage_backend.repository.PasswordResetTokenRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PasswordResetServiceTests {

	private static final Instant NOW = Instant.parse("2026-05-04T12:00:00Z");

	@Test
	void requestPasswordResetCreatesTokenAndSendsLinkForLocalUser() {
		Dependencies dependencies = dependencies();
		User user = new User("user@example.com", "stored-password-hash", "Local", "User", "Local User");
		ReflectionTestUtils.setField(user, "id", 42L);

		when(dependencies.userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(dependencies.passwordResetTokenRepository.saveAndFlush(any(PasswordResetToken.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		dependencies.service.requestPasswordReset(" User@Example.com ");

		ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
		ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
		Instant expectedExpiration = NOW.plus(Duration.ofMinutes(15));

		verify(dependencies.passwordResetTokenRepository).deleteByUserIdAndUsedAtIsNull(42L);
		verify(dependencies.passwordResetTokenRepository).saveAndFlush(tokenCaptor.capture());
		verify(dependencies.passwordResetEmailSender)
				.sendPasswordResetLink(eq(user), linkCaptor.capture(), eq(expectedExpiration));

		PasswordResetToken savedToken = tokenCaptor.getValue();
		assertThat(savedToken.getUserId()).isEqualTo(42L);
		assertThat(savedToken.getTokenHash()).hasSize(64);
		assertThat(savedToken.getExpiresAt()).isEqualTo(expectedExpiration);
		assertThat(linkCaptor.getValue()).startsWith("http://localhost:5173/reset-password?token=");
	}

	@Test
	void requestPasswordResetDoesNotRevealUnknownEmails() {
		Dependencies dependencies = dependencies();

		when(dependencies.userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

		dependencies.service.requestPasswordReset("missing@example.com");

		verify(dependencies.userRepository).findByEmail("missing@example.com");
		verifyNoInteractions(dependencies.passwordResetTokenRepository);
		verifyNoInteractions(dependencies.passwordResetEmailSender);
	}

	@Test
	void requestPasswordResetDoesNotCreateTokenForGoogleOnlyAccounts() {
		Dependencies dependencies = dependencies();
		User user = User.google("user@example.com", "google-subject", "Google", "User", "Google User");
		ReflectionTestUtils.setField(user, "id", 43L);

		when(dependencies.userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		dependencies.service.requestPasswordReset("user@example.com");

		verify(dependencies.userRepository).findByEmail("user@example.com");
		verifyNoInteractions(dependencies.passwordResetTokenRepository);
		verifyNoInteractions(dependencies.passwordResetEmailSender);
	}

	@Test
	void resetPasswordUpdatesHashMarksTokenUsedAndRevokesAuthTokens() {
		Dependencies dependencies = dependencies();
		String rawToken = "raw-reset-token";
		PasswordResetToken resetToken = new PasswordResetToken(
				42L,
				PasswordResetService.hashToken(rawToken),
				NOW.plus(Duration.ofMinutes(15)));
		User user = new User("user@example.com", "old-password-hash", "Local", "User", "Local User");
		ReflectionTestUtils.setField(user, "id", 42L);

		when(dependencies.passwordResetTokenRepository.findByTokenHash(PasswordResetService.hashToken(rawToken)))
				.thenReturn(Optional.of(resetToken));
		when(dependencies.userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(dependencies.passwordHashingService.hash("new-password")).thenReturn("new-password-hash");

		dependencies.service.resetPassword(rawToken, "new-password");

		assertThat(user.getPasswordHash()).isEqualTo("new-password-hash");
		assertThat(resetToken.getUsedAt()).isEqualTo(NOW);
		verify(dependencies.authenticationTokenRepository).deleteByUserId(42L);
		verify(dependencies.userRepository).saveAndFlush(user);
		verify(dependencies.passwordResetTokenRepository).saveAndFlush(resetToken);
	}

	@Test
	void resetPasswordRejectsExpiredToken() {
		Dependencies dependencies = dependencies();
		String rawToken = "expired-reset-token";
		PasswordResetToken resetToken = new PasswordResetToken(
				42L,
				PasswordResetService.hashToken(rawToken),
				NOW.minus(Duration.ofMinutes(1)));

		when(dependencies.passwordResetTokenRepository.findByTokenHash(PasswordResetService.hashToken(rawToken)))
				.thenReturn(Optional.of(resetToken));

		assertThatThrownBy(() -> dependencies.service.resetPassword(rawToken, "new-password"))
				.isInstanceOf(InvalidPasswordResetTokenException.class)
				.hasMessage("Password reset link is invalid or expired.");

		verify(dependencies.userRepository, never()).findById(42L);
		verifyNoInteractions(dependencies.authenticationTokenRepository);
	}

	@Test
	void resetPasswordRejectsAlreadyUsedToken() {
		Dependencies dependencies = dependencies();
		String rawToken = "used-reset-token";
		PasswordResetToken resetToken = new PasswordResetToken(
				42L,
				PasswordResetService.hashToken(rawToken),
				NOW.plus(Duration.ofMinutes(15)));
		resetToken.markUsed(NOW.minus(Duration.ofMinutes(1)));

		when(dependencies.passwordResetTokenRepository.findByTokenHash(PasswordResetService.hashToken(rawToken)))
				.thenReturn(Optional.of(resetToken));

		assertThatThrownBy(() -> dependencies.service.resetPassword(rawToken, "new-password"))
				.isInstanceOf(InvalidPasswordResetTokenException.class)
				.hasMessage("Password reset link is invalid or expired.");
	}

	private static Dependencies dependencies() {
		AuthenticationTokenRepository authenticationTokenRepository = mock(AuthenticationTokenRepository.class);
		PasswordHashingService passwordHashingService = mock(PasswordHashingService.class);
		PasswordResetEmailSender passwordResetEmailSender = mock(PasswordResetEmailSender.class);
		PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PasswordResetProperties properties = new PasswordResetProperties(
				PasswordResetDeliveryMode.LOG,
				"http://localhost:5173",
				Duration.ofMinutes(15),
				null,
				null);
		PasswordResetService service = new PasswordResetService(
				authenticationTokenRepository,
				Clock.fixed(NOW, ZoneOffset.UTC),
				passwordHashingService,
				passwordResetEmailSender,
				properties,
				passwordResetTokenRepository,
				userRepository);

		return new Dependencies(
				authenticationTokenRepository,
				passwordHashingService,
				passwordResetEmailSender,
				passwordResetTokenRepository,
				service,
				userRepository);
	}

	private record Dependencies(
			AuthenticationTokenRepository authenticationTokenRepository,
			PasswordHashingService passwordHashingService,
			PasswordResetEmailSender passwordResetEmailSender,
			PasswordResetTokenRepository passwordResetTokenRepository,
			PasswordResetService service,
			UserRepository userRepository) {
	}
}
