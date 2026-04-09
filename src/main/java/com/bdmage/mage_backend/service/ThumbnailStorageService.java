package com.bdmage.mage_backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface ThumbnailStorageService {

	/**
	 * Stores a thumbnail image file for the given preset and returns
	 * a reference string that can be persisted as the preset's thumbnailRef.
	 *
	 * @param file     the uploaded image file
	 * @param presetId the ID of the preset this thumbnail belongs to
	 * @return a non-null reference string identifying the stored thumbnail
	 */
	String store(MultipartFile file, Long presetId);
}
