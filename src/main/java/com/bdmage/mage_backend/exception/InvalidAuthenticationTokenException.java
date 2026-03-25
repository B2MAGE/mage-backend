package com.bdmage.mage_backend.exception;

public class InvalidAuthenticationTokenException extends RuntimeException {

	public InvalidAuthenticationTokenException(String message) {
		super(message);
	}
}
