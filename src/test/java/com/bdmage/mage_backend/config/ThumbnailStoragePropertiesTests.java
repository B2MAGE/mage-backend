package com.bdmage.mage_backend.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailStoragePropertiesTests {

	@Test
	void awsS3ConfigurationValidatesWithoutEndpointOverride() {
		ThumbnailStorageProperties properties = new ThumbnailStorageProperties(
				"aws-s3",
				"us-east-1",
				"mage-test-thumbnails",
				"presets",
				null,
				null,
				null,
				null,
				null,
				"https://cdn.example.com",
				"image/png",
				5L * 1024 * 1024,
				Duration.ofMinutes(10));

		properties.validate();

		assertThat(properties.providerType()).isEqualTo(ThumbnailStorageProvider.AWS_S3);
		assertThat(properties.usePathStyleAccess()).isFalse();
	}

	@Test
	void minioConfigurationRequiresEndpointAndStaticCredentials() {
		ThumbnailStorageProperties properties = new ThumbnailStorageProperties(
				"minio",
				"us-east-1",
				"mage-local-thumbnails",
				"presets",
				null,
				null,
				null,
				null,
				null,
				"http://localhost:9000/mage-local-thumbnails",
				"image/png",
				5L * 1024 * 1024,
				Duration.ofMinutes(10));

		assertThatThrownBy(properties::validate)
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("MAGE_THUMBNAIL_ENDPOINT is required.");
	}

	@Test
	void minioDefaultsToPathStyleAccess() {
		ThumbnailStorageProperties properties = new ThumbnailStorageProperties(
				"minio",
				"us-east-1",
				"mage-local-thumbnails",
				"presets",
				"http://minio:9000",
				"http://localhost:9000",
				"minioadmin",
				"minioadmin",
				null,
				"http://localhost:9000/mage-local-thumbnails",
				"image/png",
				5L * 1024 * 1024,
				Duration.ofMinutes(10));

		properties.validate();

		assertThat(properties.providerType()).isEqualTo(ThumbnailStorageProvider.MINIO);
		assertThat(properties.usePathStyleAccess()).isTrue();
	}
}
