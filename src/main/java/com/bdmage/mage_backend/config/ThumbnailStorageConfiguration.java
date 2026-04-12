package com.bdmage.mage_backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ThumbnailStorageProperties.class)
public class ThumbnailStorageConfiguration {

	@Bean
	S3Client s3Client(ThumbnailStorageProperties properties) {
		properties.validate();

		return S3Client.builder()
				.region(Region.of(properties.region()))
				.build();
	}

	@Bean
	S3Presigner s3Presigner(ThumbnailStorageProperties properties) {
		properties.validate();

		return S3Presigner.builder()
				.region(Region.of(properties.region()))
				.build();
	}
}
