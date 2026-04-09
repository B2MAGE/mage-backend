package com.bdmage.mage_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class LocalThumbnailStorageService implements ThumbnailStorageService {

	private final Path storageRoot;

	public LocalThumbnailStorageService(
			@Value("${mage.thumbnail.storage-path}") String storagePath) {
		this.storageRoot = Paths.get(storagePath);
	}

	@Override
	public String store(MultipartFile file, Long presetId) {
		try {
			String extension = resolveExtension(file.getContentType());
			String filename = UUID.randomUUID() + extension;
			String relativeRef = "thumbnails/" + presetId + "/" + filename;

			Path targetDir = this.storageRoot.resolve(String.valueOf(presetId));
			Files.createDirectories(targetDir);

			Path targetFile = targetDir.resolve(filename);
			file.transferTo(targetFile);

			return relativeRef;
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to store thumbnail.", ex);
		}
	}

	private static String resolveExtension(String contentType) {
		if (contentType == null) {
			return "";
		}
		return switch (contentType) {
			case "image/jpeg" -> ".jpg";
			case "image/png" -> ".png";
			case "image/webp" -> ".webp";
			case "image/gif" -> ".gif";
			default -> "";
		};
	}
}
