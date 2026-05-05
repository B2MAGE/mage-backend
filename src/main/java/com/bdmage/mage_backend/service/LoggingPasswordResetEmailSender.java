package com.bdmage.mage_backend.service;

import java.time.Instant;

import com.bdmage.mage_backend.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "mage.password-reset", name = "delivery", havingValue = "log", matchIfMissing = true)
public class LoggingPasswordResetEmailSender implements PasswordResetEmailSender {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingPasswordResetEmailSender.class);

	@Override
	public void sendPasswordResetLink(User user, String resetLink, Instant expiresAt) {
		LOGGER.info(
				"Password reset link for {} expires at {}: {}",
				user.getEmail(),
				expiresAt,
				resetLink);
	}
}
