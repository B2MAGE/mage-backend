package com.bdmage.mage_backend.service;

import java.time.Instant;

import com.bdmage.mage_backend.model.User;

public interface PasswordResetEmailSender {

	void sendPasswordResetLink(User user, String resetLink, Instant expiresAt);
}
