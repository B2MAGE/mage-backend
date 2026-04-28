package com.bdmage.mage_backend.exception;

// Thrown when a reset token is missing, already used, or past its expiry window
public class InvalidPasswordResetTokenException extends RuntimeException {

	public InvalidPasswordResetTokenException(String message) {
		super(message);
	}

}
