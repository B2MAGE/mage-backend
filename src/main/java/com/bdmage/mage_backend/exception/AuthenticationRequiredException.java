package com.bdmage.mage_backend.exception;

public class AuthenticationRequiredException extends RuntimeException {

	public AuthenticationRequiredException(String message) {
		super(message);
	}
}
