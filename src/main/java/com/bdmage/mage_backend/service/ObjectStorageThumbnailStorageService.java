package com.bdmage.mage_backend.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.bdmage.mage_backend.config.ThumbnailStorageProperties;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.exception.ThumbnailStorageUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class ObjectStorageThumbnailStorageService implements ThumbnailStorageService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorageThumbnailStorageService.class);
	private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
			"image/jpeg", "jpg",
			"image/png", "png",
			"image/webp", "webp",
			"image/gif", "gif");
	private static final String INVALID_UPLOAD_KEY_MESSAGE = "Thumbnail upload key is invalid.";
	private static final String THUMBNAIL_NOT_FOUND_MESSAGE = "Uploaded thumbnail was not found in storage.";
	private static final String STORAGE_UNAVAILABLE_MESSAGE = "Thumbnail storage is unavailable right now.";

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final ThumbnailStorageProperties properties;

	public ObjectStorageThumbnailStorageService(
			S3Client s3Client,
			S3Presigner s3Presigner,
			ThumbnailStorageProperties properties) {
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
		this.properties = properties;
	}

	@Override
	public PresignedThumbnailUpload createSceneCreationUpload(Long ownerUserId, String filename, String contentType) {
		String objectKey = buildPendingObjectKey(ownerUserId, filename, contentType);
		return createPresignedUpload(objectKey, contentType);
	}

	@Override
	public FinalizedThumbnail finalizeSceneCreationUpload(Long ownerUserId, String objectKey) {
		String normalizedObjectKey = normalizePendingObjectKey(ownerUserId, objectKey);
		return finalizeStoredObject(normalizedObjectKey);
	}

	@Override
	public PresignedThumbnailUpload createPresignedUpload(Long sceneId, String filename, String contentType) {
		String objectKey = buildSceneObjectKey(sceneId, filename, contentType);
		return createPresignedUpload(objectKey, contentType);
	}

	@Override
	public FinalizedThumbnail finalizeUpload(Long sceneId, String objectKey) {
		String normalizedObjectKey = normalizeObjectKey(sceneId, objectKey);
		return finalizeStoredObject(normalizedObjectKey);
	}

	private PresignedThumbnailUpload createPresignedUpload(String objectKey, String contentType) {
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(this.properties.bucket())
				.key(objectKey)
				.contentType(contentType)
				.build();
		PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
				.signatureDuration(this.properties.presignDuration())
				.putObjectRequest(putObjectRequest)
				.build();

		try {
			PresignedPutObjectRequest presignedRequest = this.s3Presigner.presignPutObject(presignRequest);

			return new PresignedThumbnailUpload(
					objectKey,
					presignedRequest.url().toString(),
					presignedRequest.httpRequest().method().name(),
					Map.of("Content-Type", contentType),
					Instant.now().plus(this.properties.presignDuration()));
		} catch (RuntimeException ex) {
			throw new ThumbnailStorageUnavailableException(STORAGE_UNAVAILABLE_MESSAGE, ex);
		}
	}

	private FinalizedThumbnail finalizeStoredObject(String normalizedObjectKey) {
		try {
			HeadObjectResponse headObjectResponse = this.s3Client.headObject(HeadObjectRequest.builder()
					.bucket(this.properties.bucket())
					.key(normalizedObjectKey)
					.build());
			validateStoredObject(headObjectResponse);

			return new FinalizedThumbnail(
					normalizedObjectKey,
					this.properties.toPublicUrl(normalizedObjectKey));
		} catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				throw new InvalidThumbnailException(THUMBNAIL_NOT_FOUND_MESSAGE);
			}
			throw new ThumbnailStorageUnavailableException(STORAGE_UNAVAILABLE_MESSAGE, ex);
		}
	}

	@Override
	public void delete(String thumbnailRef) {
		String objectKey = extractObjectKey(thumbnailRef);

		if (!StringUtils.hasText(objectKey)) {
			return;
		}

		try {
			this.s3Client.deleteObject(DeleteObjectRequest.builder()
					.bucket(this.properties.bucket())
					.key(objectKey)
					.build());
		} catch (S3Exception ex) {
			LOGGER.warn("Failed to delete thumbnail {}", thumbnailRef, ex);
		}
	}

	private String buildSceneObjectKey(Long sceneId, String filename, String contentType) {
		String extension = CONTENT_TYPE_EXTENSIONS.get(contentType);

		if (!StringUtils.hasText(extension)) {
			throw new InvalidThumbnailException("Thumbnail must be a valid image (jpeg, png, webp, or gif).");
		}

		return this.properties.normalizedKeyPrefix()
				+ "/"
				+ sceneId
				+ "/thumbnails/"
				+ UUID.randomUUID()
				+ "."
				+ extension;
	}

	private String buildPendingObjectKey(Long ownerUserId, String filename, String contentType) {
		String extension = CONTENT_TYPE_EXTENSIONS.get(contentType);

		if (!StringUtils.hasText(extension)) {
			throw new InvalidThumbnailException("Thumbnail must be a valid image (jpeg, png, webp, or gif).");
		}

		return this.properties.normalizedKeyPrefix()
				+ "/pending/"
				+ ownerUserId
				+ "/thumbnails/"
				+ UUID.randomUUID()
				+ "."
				+ extension;
	}

	private String normalizeObjectKey(Long sceneId, String objectKey) {
		if (!StringUtils.hasText(objectKey)) {
			throw new InvalidThumbnailException(INVALID_UPLOAD_KEY_MESSAGE);
		}

		String normalizedObjectKey = objectKey.trim();
		String expectedPrefix = this.properties.normalizedKeyPrefix() + "/" + sceneId + "/thumbnails/";

		if (!normalizedObjectKey.startsWith(expectedPrefix)) {
			throw new InvalidThumbnailException(INVALID_UPLOAD_KEY_MESSAGE);
		}

		return normalizedObjectKey;
	}

	private String normalizePendingObjectKey(Long ownerUserId, String objectKey) {
		if (!StringUtils.hasText(objectKey)) {
			throw new InvalidThumbnailException(INVALID_UPLOAD_KEY_MESSAGE);
		}

		String normalizedObjectKey = objectKey.trim();
		String expectedPrefix = this.properties.normalizedKeyPrefix() + "/pending/" + ownerUserId + "/thumbnails/";

		if (!normalizedObjectKey.startsWith(expectedPrefix)) {
			throw new InvalidThumbnailException(INVALID_UPLOAD_KEY_MESSAGE);
		}

		return normalizedObjectKey;
	}

	private void validateStoredObject(HeadObjectResponse headObjectResponse) {
		if (!this.properties.allowedContentTypeSet().contains(headObjectResponse.contentType())) {
			throw new InvalidThumbnailException("Thumbnail must be a valid image (jpeg, png, webp, or gif).");
		}
		if (headObjectResponse.contentLength() <= 0L) {
			throw new InvalidThumbnailException("Thumbnail file must not be empty.");
		}
		if (headObjectResponse.contentLength() > this.properties.maxBytes()) {
			throw new InvalidThumbnailException("Thumbnail must not exceed 5 MB.");
		}
	}

	private String extractObjectKey(String thumbnailRef) {
		if (!StringUtils.hasText(thumbnailRef)) {
			return null;
		}

		String trimmedRef = thumbnailRef.trim();
		String publicBaseUrlPrefix = this.properties.normalizedPublicBaseUrl() + "/";

		if (!trimmedRef.startsWith(publicBaseUrlPrefix)) {
			String keyPrefix = this.properties.normalizedKeyPrefix() + "/";
			return trimmedRef.startsWith(keyPrefix) ? trimmedRef : null;
		}

		String objectKey = trimmedRef.substring(publicBaseUrlPrefix.length());

		return StringUtils.hasText(objectKey) ? objectKey : null;
	}
}
