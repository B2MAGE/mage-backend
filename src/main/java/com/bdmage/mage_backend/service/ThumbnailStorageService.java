package com.bdmage.mage_backend.service;

import java.time.Instant;
import java.util.Map;

public interface ThumbnailStorageService {

	PresignedThumbnailUpload createSceneCreationUpload(Long ownerUserId, String filename, String contentType);

	FinalizedThumbnail finalizeSceneCreationUpload(Long ownerUserId, String objectKey);

	PresignedThumbnailUpload createPresignedUpload(Long sceneId, String filename, String contentType);

	FinalizedThumbnail finalizeUpload(Long sceneId, String objectKey);

	PresignedThumbnailUpload createTemplateUpload(Long ownerUserId, String filename, String contentType);

	FinalizedThumbnail copyTemplateUpload(Long ownerUserId, String objectKey, String destinationKey);

	/** Unlike best-effort scene cleanup, template cleanup must retry failures. */
	void deleteTemplateAsset(String thumbnailRef);

	default void delete(String thumbnailRef) {
		// Optional for storage implementations that do not need cleanup.
	}

	record PresignedThumbnailUpload(
			String objectKey,
			String uploadUrl,
			String method,
			Map<String, String> headers,
			Instant expiresAt) {
	}

	record FinalizedThumbnail(String objectKey, String publicUrl) {
	}
}
