package com.bdmage.mage_backend.service;

import java.time.Instant;
import java.util.Map;

public interface ThumbnailStorageService {

	PresignedThumbnailUpload createPresetCreationUpload(Long ownerUserId, String filename, String contentType);

	FinalizedThumbnail finalizePresetCreationUpload(Long ownerUserId, String objectKey);

	PresignedThumbnailUpload createPresignedUpload(Long presetId, String filename, String contentType);

	FinalizedThumbnail finalizeUpload(Long presetId, String objectKey);

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
