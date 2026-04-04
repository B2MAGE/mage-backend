package com.bdmage.mage_backend.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "mage.storage.thumbnail")
public record ThumbnailStorageProperties(String uploadDir, DataSize maxFileSize) {

	public void validate() {
		requireValue("MAGE_STORAGE_THUMBNAIL_UPLOAD_DIR", uploadDir);

		if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
			throw new IllegalStateException("MAGE_STORAGE_THUMBNAIL_MAX_FILE_SIZE must be greater than zero.");
		}
	}

	public Path uploadDirectoryPath() {
		return Path.of(uploadDir).toAbsolutePath().normalize();
	}

	private static void requireValue(String environmentVariable, String value) {
		if (!StringUtils.hasText(value) || isUnresolvedPlaceholder(value)) {
			throw new IllegalStateException(environmentVariable + " is required.");
		}
	}

	private static boolean isUnresolvedPlaceholder(String value) {
		return value.startsWith("${") && value.endsWith("}");
	}
}
