package com.bdmage.mage_backend.exception;

public class AccountConflictException extends RuntimeException {

	public AccountConflictException(String message) {
		super(message);
	}
}
