package com.bdmage.mage_backend.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.S3Presigner.Builder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ThumbnailStorageProperties.class)
public class ThumbnailStorageConfiguration {

	@Bean
	S3Client s3Client(ThumbnailStorageProperties properties) {
		properties.validate();

		S3ClientBuilder builder = S3Client.builder()
				.region(Region.of(properties.region()))
				.serviceConfiguration(s3Configuration(properties));
		configureEndpoint(builder, properties.normalizedEndpoint());
		configureCredentials(builder, properties);

		return builder.build();
	}

	@Bean
	S3Presigner s3Presigner(ThumbnailStorageProperties properties) {
		properties.validate();

		Builder builder = S3Presigner.builder()
				.region(Region.of(properties.region()))
				.serviceConfiguration(s3Configuration(properties));
		configureEndpoint(builder, StringUtils.hasText(properties.normalizedPresignEndpoint())
				? properties.normalizedPresignEndpoint()
				: properties.normalizedEndpoint());
		configureCredentials(builder, properties);

		return builder.build();
	}

	private static void configureEndpoint(S3ClientBuilder builder, String endpoint) {
		if (StringUtils.hasText(endpoint)) {
			builder.endpointOverride(URI.create(endpoint));
		}
	}

	private static void configureEndpoint(Builder builder, String endpoint) {
		if (StringUtils.hasText(endpoint)) {
			builder.endpointOverride(URI.create(endpoint));
		}
	}

	private static void configureCredentials(S3ClientBuilder builder, ThumbnailStorageProperties properties) {
		if (properties.hasStaticCredentials()) {
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())));
		}
	}

	private static void configureCredentials(Builder builder, ThumbnailStorageProperties properties) {
		if (properties.hasStaticCredentials()) {
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())));
		}
	}

	private static S3Configuration s3Configuration(ThumbnailStorageProperties properties) {
		return S3Configuration.builder()
				.pathStyleAccessEnabled(properties.usePathStyleAccess())
				.build();
	}
}
