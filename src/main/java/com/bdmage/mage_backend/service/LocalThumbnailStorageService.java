package com.bdmage.mage_backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalThumbnailStorageService implements ThumbnailStorageService {

	private static final Logger LOGGER = LoggerFactory.getLogger(LocalThumbnailStorageService.class);
	private static final String THUMBNAIL_REF_PREFIX = "thumbnails/";

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

	@Override
	public void delete(String thumbnailRef) {
		Path targetFile = resolveStoredFile(thumbnailRef);
		if (targetFile == null) {
			return;
		}

		try {
			Files.deleteIfExists(targetFile);
		} catch (IOException ex) {
			LOGGER.warn("Failed to delete thumbnail {}", thumbnailRef, ex);
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

	private Path resolveStoredFile(String thumbnailRef) {
		if (!StringUtils.hasText(thumbnailRef) || !thumbnailRef.startsWith(THUMBNAIL_REF_PREFIX)) {
			return null;
		}

		Path relativePath = Paths.get(thumbnailRef.substring(THUMBNAIL_REF_PREFIX.length())).normalize();
		if (relativePath.getNameCount() == 0 || relativePath.startsWith("..")) {
			return null;
		}

		Path resolvedPath = this.storageRoot.resolve(relativePath).normalize();
		Path normalizedRoot = this.storageRoot.normalize();
		if (!resolvedPath.startsWith(normalizedRoot)) {
			return null;
		}

		return resolvedPath;
	}
}
