package com.bdmage.mage_backend.config;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "mage.thumbnail")
public record ThumbnailStorageProperties(
		String provider,
		String region,
		String bucket,
		String keyPrefix,
		String endpoint,
		String presignEndpoint,
		String accessKeyId,
		String secretAccessKey,
		Boolean pathStyleAccess,
		String publicBaseUrl,
		String allowedContentTypes,
		Long maxBytes,
		Duration presignDuration) {

	public void validate() {
		providerType();
		requireValue("MAGE_THUMBNAIL_REGION", region);
		requireValue("MAGE_THUMBNAIL_BUCKET", bucket);
		requireValue("MAGE_THUMBNAIL_PUBLIC_BASE_URL", publicBaseUrl);

		if (providerType() == ThumbnailStorageProvider.MINIO) {
			requireValue("MAGE_THUMBNAIL_ENDPOINT", endpoint);
			requireValue("MAGE_THUMBNAIL_ACCESS_KEY_ID", accessKeyId);
			requireValue("MAGE_THUMBNAIL_SECRET_ACCESS_KEY", secretAccessKey);
		}
		if (StringUtils.hasText(accessKeyId) != StringUtils.hasText(secretAccessKey)) {
			throw new IllegalStateException(
					"MAGE_THUMBNAIL_ACCESS_KEY_ID and MAGE_THUMBNAIL_SECRET_ACCESS_KEY must both be set together.");
		}

		if (allowedContentTypeSet().isEmpty()) {
			throw new IllegalStateException("MAGE_THUMBNAIL_ALLOWED_CONTENT_TYPES must contain at least one content type.");
		}
		if (maxBytes == null || maxBytes <= 0L) {
			throw new IllegalStateException("MAGE_THUMBNAIL_MAX_BYTES must be greater than zero.");
		}
		if (presignDuration == null || presignDuration.isNegative() || presignDuration.isZero()) {
			throw new IllegalStateException("MAGE_THUMBNAIL_PRESIGN_DURATION must be greater than zero.");
		}

		try {
			URI.create(normalizedPublicBaseUrl());
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException("MAGE_THUMBNAIL_PUBLIC_BASE_URL must be a valid URL.", ex);
		}

		validateOptionalUrl("MAGE_THUMBNAIL_ENDPOINT", endpoint);
		validateOptionalUrl("MAGE_THUMBNAIL_PRESIGN_ENDPOINT", presignEndpoint);
	}

	public ThumbnailStorageProvider providerType() {
		return ThumbnailStorageProvider.fromValue(provider);
	}

	public Set<String> allowedContentTypeSet() {
		if (!StringUtils.hasText(allowedContentTypes)) {
			return Set.of();
		}

		return Arrays.stream(allowedContentTypes.split(","))
				.map(String::trim)
				.filter(StringUtils::hasText)
				.collect(Collectors.toUnmodifiableSet());
	}

	public String normalizedKeyPrefix() {
		if (!StringUtils.hasText(keyPrefix)) {
			return "scenes";
		}

		String trimmed = keyPrefix.trim();
		while (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}

		return trimmed;
	}

	public String normalizedPublicBaseUrl() {
		return publicBaseUrl.trim().replaceAll("/+$", "");
	}

	public String normalizedEndpoint() {
		if (!StringUtils.hasText(endpoint)) {
			return null;
		}

		return endpoint.trim().replaceAll("/+$", "");
	}

	public String normalizedPresignEndpoint() {
		if (!StringUtils.hasText(presignEndpoint)) {
			return null;
		}

		return presignEndpoint.trim().replaceAll("/+$", "");
	}

	public boolean usePathStyleAccess() {
		if (pathStyleAccess != null) {
			return pathStyleAccess;
		}

		return providerType() == ThumbnailStorageProvider.MINIO;
	}

	public String toPublicUrl(String objectKey) {
		return normalizedPublicBaseUrl() + "/" + objectKey;
	}

	public boolean hasStaticCredentials() {
		return StringUtils.hasText(accessKeyId) && StringUtils.hasText(secretAccessKey);
	}

	private static void requireValue(String environmentVariable, String value) {
		if (!StringUtils.hasText(value) || isUnresolvedPlaceholder(value)) {
			throw new IllegalStateException(environmentVariable + " is required.");
		}
	}

	private static boolean isUnresolvedPlaceholder(String value) {
		return value.startsWith("${") && value.endsWith("}");
	}

	private static void validateOptionalUrl(String environmentVariable, String value) {
		if (!StringUtils.hasText(value)) {
			return;
		}

		try {
			URI.create(value.trim());
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException(environmentVariable + " must be a valid URL.", ex);
		}
	}
}
