package com.bdmage.mage_backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.bdmage.mage_backend.config.ThumbnailStorageProperties;
import com.bdmage.mage_backend.exception.InvalidThumbnailUploadException;
import com.bdmage.mage_backend.exception.ThumbnailStorageException;
import com.bdmage.mage_backend.exception.UnsupportedThumbnailContentTypeException;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileSystemThumbnailStorageService implements ThumbnailStorageService {

	private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
			MediaType.IMAGE_PNG_VALUE, "png",
			MediaType.IMAGE_JPEG_VALUE, "jpg",
			"image/webp", "webp");
	private static final int MAX_FILENAME_ATTEMPTS = 10;

	private final Path uploadDirectory;
	private final long maxFileSizeBytes;

	public FileSystemThumbnailStorageService(ThumbnailStorageProperties properties) {
		properties.validate();
		this.uploadDirectory = properties.uploadDirectoryPath();
		this.maxFileSizeBytes = properties.maxFileSize().toBytes();
	}

	@Override
	public String storePresetThumbnail(Long presetId, MultipartFile file) {
		Objects.requireNonNull(presetId, "presetId must not be null");
		validateUpload(file);

		String normalizedContentType = normalizeContentType(file.getContentType());
		String extension = CONTENT_TYPE_EXTENSIONS.get(normalizedContentType);
		if (extension == null) {
			throw new UnsupportedThumbnailContentTypeException(
					"Supported thumbnail types are image/png, image/jpeg, and image/webp.");
		}

		try {
			Files.createDirectories(this.uploadDirectory);
			return writeThumbnailFile(presetId, file, extension);
		} catch (IOException ex) {
			throw new ThumbnailStorageException("Failed to store thumbnail file.", ex);
		}
	}

	private void validateUpload(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new InvalidThumbnailUploadException("Thumbnail file must not be empty.");
		}

		if (file.getSize() > this.maxFileSizeBytes) {
			throw new InvalidThumbnailUploadException("Thumbnail file exceeds the configured size limit.");
		}

		if (!StringUtils.hasText(file.getContentType())) {
			throw new InvalidThumbnailUploadException("Thumbnail content type is required.");
		}
	}

	private static String normalizeContentType(String contentType) {
		try {
			MediaType parsedContentType = MediaType.parseMediaType(contentType);
			return new MediaType(parsedContentType.getType(), parsedContentType.getSubtype())
					.toString()
					.toLowerCase(Locale.ROOT);
		} catch (InvalidMediaTypeException ex) {
			throw new InvalidThumbnailUploadException("Thumbnail content type is invalid.");
		}
	}

	private String writeThumbnailFile(Long presetId, MultipartFile file, String extension) throws IOException {
		for (int attempt = 0; attempt < MAX_FILENAME_ATTEMPTS; attempt++) {
			String thumbnailFilename = createThumbnailFilename(presetId, extension);
			Path targetFile = this.uploadDirectory.resolve(thumbnailFilename).normalize();

			if (!targetFile.startsWith(this.uploadDirectory)) {
				throw new ThumbnailStorageException("Resolved thumbnail path is outside the configured upload directory.");
			}

			try (InputStream inputStream = file.getInputStream();
					OutputStream outputStream = Files.newOutputStream(
							targetFile,
							StandardOpenOption.CREATE_NEW,
							StandardOpenOption.WRITE)) {
				inputStream.transferTo(outputStream);
				return thumbnailFilename;
			} catch (FileAlreadyExistsException ex) {
				continue;
			}
		}

		throw new ThumbnailStorageException("Unable to allocate a unique thumbnail filename.");
	}

	private static String createThumbnailFilename(Long presetId, String extension) {
		return "preset_%d_%s.%s".formatted(
				presetId,
				UUID.randomUUID().toString().replace("-", ""),
				extension);
	}
}
