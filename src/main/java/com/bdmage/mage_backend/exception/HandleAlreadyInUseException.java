package com.bdmage.mage_backend.exception;

public class HandleAlreadyInUseException extends RuntimeException {

	public HandleAlreadyInUseException(String message) {
		super(message);
	}
}
