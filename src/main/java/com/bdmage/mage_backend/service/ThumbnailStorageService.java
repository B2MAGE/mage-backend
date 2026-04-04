package com.bdmage.mage_backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface ThumbnailStorageService {

	String storePresetThumbnail(Long presetId, MultipartFile file);
}
