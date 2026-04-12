package com.bdmage.mage_backend.exception;

public class ThumbnailStorageUnavailableException extends RuntimeException {

	public ThumbnailStorageUnavailableException(String message) {
		super(message);
	}

	public ThumbnailStorageUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
