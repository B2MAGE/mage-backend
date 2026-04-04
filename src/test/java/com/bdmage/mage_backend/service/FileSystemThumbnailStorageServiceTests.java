package com.bdmage.mage_backend.service;

import java.nio.file.Files;
import java.nio.file.Path;

import com.bdmage.mage_backend.config.ThumbnailStorageProperties;
import com.bdmage.mage_backend.exception.InvalidThumbnailUploadException;
import com.bdmage.mage_backend.exception.ThumbnailStorageException;
import com.bdmage.mage_backend.exception.UnsupportedThumbnailContentTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemThumbnailStorageServiceTests {

	@TempDir
	Path tempDirectory;

	@Test
	void storePresetThumbnailWritesFileToDiskWithGeneratedFilename() throws Exception {
		Path uploadDirectory = this.tempDirectory.resolve("uploads");
		FileSystemThumbnailStorageService storageService = storageService(uploadDirectory, DataSize.ofMegabytes(5));
		byte[] fileContents = "thumbnail-data".getBytes();
		MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", fileContents);

		String storedFilename = storageService.storePresetThumbnail(15L, file);
		Path storedFile = uploadDirectory.resolve(storedFilename);

		assertThat(Files.exists(uploadDirectory)).isTrue();
		assertThat(Files.exists(storedFile)).isTrue();
		assertThat(storedFilename).startsWith("preset_15_").endsWith(".png");
		assertThat(storedFilename).isNotEqualTo("cover.png");
		assertThat(Files.readAllBytes(storedFile)).isEqualTo(fileContents);
	}

	@Test
	void storePresetThumbnailRejectsUnsupportedContentType() {
		FileSystemThumbnailStorageService storageService = storageService(
				this.tempDirectory.resolve("uploads"),
				DataSize.ofMegabytes(5));
		MockMultipartFile file = new MockMultipartFile("file", "cover.gif", "image/gif", "gif-data".getBytes());

		assertThatThrownBy(() -> storageService.storePresetThumbnail(15L, file))
				.isInstanceOf(UnsupportedThumbnailContentTypeException.class)
				.hasMessage("Supported thumbnail types are image/png, image/jpeg, and image/webp.");
	}

	@Test
	void storePresetThumbnailRejectsEmptyUploads() {
		FileSystemThumbnailStorageService storageService = storageService(
				this.tempDirectory.resolve("uploads"),
				DataSize.ofMegabytes(5));
		MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[0]);

		assertThatThrownBy(() -> storageService.storePresetThumbnail(15L, file))
				.isInstanceOf(InvalidThumbnailUploadException.class)
				.hasMessage("Thumbnail file must not be empty.");
	}

	@Test
	void storePresetThumbnailFailsCleanlyWhenUploadDirectoryCannotBeCreated() throws Exception {
		Path uploadPath = Files.writeString(this.tempDirectory.resolve("uploads"), "not-a-directory");
		FileSystemThumbnailStorageService storageService = storageService(uploadPath, DataSize.ofMegabytes(5));
		MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", "thumbnail-data".getBytes());

		assertThatThrownBy(() -> storageService.storePresetThumbnail(15L, file))
				.isInstanceOf(ThumbnailStorageException.class)
				.hasMessage("Failed to store thumbnail file.");
	}

	private static FileSystemThumbnailStorageService storageService(Path uploadDirectory, DataSize maxFileSize) {
		return new FileSystemThumbnailStorageService(
				new ThumbnailStorageProperties(uploadDirectory.toString(), maxFileSize));
	}
}
