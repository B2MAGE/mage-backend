package com.bdmage.mage_backend.service;

import java.time.Instant;

import com.bdmage.mage_backend.config.PasswordResetProperties;
import com.bdmage.mage_backend.exception.PasswordResetDeliveryException;
import com.bdmage.mage_backend.model.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "mage.password-reset", name = "delivery", havingValue = "smtp")
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {

	private static final String DELIVERY_FAILURE_MESSAGE = "Password reset email could not be sent.";

	private final JavaMailSender mailSender;
	private final PasswordResetProperties properties;

	public SmtpPasswordResetEmailSender(JavaMailSender mailSender, PasswordResetProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public void sendPasswordResetLink(User user, String resetLink, Instant expiresAt) {
		SimpleMailMessage message = new SimpleMailMessage();
		if (StringUtils.hasText(this.properties.fromEmail())) {
			message.setFrom(this.properties.fromEmail().trim());
		}
		message.setTo(user.getEmail());
		message.setSubject("Reset your MAGE password");
		message.setText("""
				Hi %s,

				Use this link to reset your MAGE password:

				%s

				This link expires at %s. If you did not request a password reset, you can ignore this email.
				""".formatted(user.getDisplayName(), resetLink, expiresAt));

		try {
			this.mailSender.send(message);
		} catch (MailException ex) {
			throw new PasswordResetDeliveryException(DELIVERY_FAILURE_MESSAGE, ex);
		}
	}
}
