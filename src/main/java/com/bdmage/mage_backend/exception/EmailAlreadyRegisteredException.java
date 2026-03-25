package com.bdmage.mage_backend.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException(String message) {
		super(message);
	}
}
