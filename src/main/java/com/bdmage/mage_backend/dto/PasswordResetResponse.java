package com.bdmage.mage_backend.dto;

// Safe response — same message regardless of whether the email exists or not.
// resetToken is included so the confirm flow can be tested without an email server;
// in a real deployment the token would be emailed and this field would be left null.
public record PasswordResetResponse(String message, String resetToken) {
}
