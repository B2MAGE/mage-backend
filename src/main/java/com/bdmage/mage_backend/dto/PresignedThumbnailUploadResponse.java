package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.Map;

import com.bdmage.mage_backend.service.ThumbnailStorageService;

public record PresignedThumbnailUploadResponse(
		String objectKey,
		String uploadUrl,
		String method,
		Map<String, String> headers,
		Instant expiresAt) {

	public static PresignedThumbnailUploadResponse from(ThumbnailStorageService.PresignedThumbnailUpload upload) {
		return new PresignedThumbnailUploadResponse(
				upload.objectKey(),
				upload.uploadUrl(),
				upload.method(),
				upload.headers(),
				upload.expiresAt());
	}
}
