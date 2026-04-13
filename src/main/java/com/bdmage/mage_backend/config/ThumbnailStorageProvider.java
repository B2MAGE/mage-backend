package com.bdmage.mage_backend.config;

import java.util.Locale;

public enum ThumbnailStorageProvider {

	AWS_S3("aws-s3"),
	MINIO("minio");

	private final String value;

	ThumbnailStorageProvider(String value) {
		this.value = value;
	}

	public String value() {
		return this.value;
	}

	public static ThumbnailStorageProvider fromValue(String value) {
		if (value == null) {
			return AWS_S3;
		}

		String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
		for (ThumbnailStorageProvider provider : values()) {
			if (provider.value.equals(normalizedValue)) {
				return provider;
			}
		}

		throw new IllegalStateException("MAGE_THUMBNAIL_PROVIDER must be one of: aws-s3, minio.");
	}
}
