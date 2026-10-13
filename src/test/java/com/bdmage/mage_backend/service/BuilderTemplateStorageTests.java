package com.bdmage.mage_backend.service;

import java.time.Duration;
import com.bdmage.mage_backend.config.ThumbnailStorageProperties;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.exception.ThumbnailStorageUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class BuilderTemplateStorageTests {
    private final S3Client s3 = mock(S3Client.class);
    private final ObjectStorageThumbnailStorageService storage = new ObjectStorageThumbnailStorageService(this.s3,
            mock(S3Presigner.class), new ThumbnailStorageProperties("aws-s3", "us-east-1", "test-bucket", "scenes",
            null, null, null, null, false, "https://cdn.example.test", "image/png,image/jpeg", 5242880L, Duration.ofMinutes(10)));
    private final String pending = "scenes/builder-templates/pending/12/12345678-abcd.png";
    private final String saved = "scenes/builder-templates/saved/7/unique";

    @Test
    void validatesTheImageThenCopiesThatExactVersionToAnIndependentKey() {
        when(this.s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("image/png").contentLength(100L).eTag("version-1").build());
        var result = this.storage.copyTemplateUpload(12L, this.pending, this.saved);
        assertThat(result.publicUrl()).isEqualTo("https://cdn.example.test/" + this.saved);
        var copied = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(this.s3).copyObject(copied.capture());
        assertThat(copied.getValue().sourceKey()).isEqualTo(this.pending);
        assertThat(copied.getValue().destinationKey()).isEqualTo(this.saved);
        assertThat(copied.getValue().copySourceIfMatch()).isEqualTo("version-1");
    }

    @Test
    void rejectsOtherOwnersSceneAssetsTraversalAndNonImages() {
        for (String key : java.util.List.of("scenes/1/thumbnails/a.png", "scenes/builder-templates/pending/13/a.png",
                "scenes/builder-templates/pending/12/../13/a.png", "scenes/builder-templates/saved/2/a.png")) {
            assertThatThrownBy(() -> this.storage.copyTemplateUpload(12L, key, this.saved)).isInstanceOf(InvalidThumbnailException.class);
        }
        verifyNoInteractions(this.s3);
        when(this.s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentType("text/html").contentLength(100L).build());
        assertThatThrownBy(() -> this.storage.copyTemplateUpload(12L, this.pending, this.saved)).isInstanceOf(InvalidThumbnailException.class);
        verify(this.s3, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void cleanupIsScopedAndThrowsSoItsPersistentQueueCanRetry() {
        assertThatThrownBy(() -> this.storage.deleteTemplateAsset("scenes/1/thumbnails/a.png")).isInstanceOf(InvalidThumbnailException.class);
        when(this.s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        assertThatThrownBy(() -> this.storage.deleteTemplateAsset("https://cdn.example.test/" + this.saved))
                .isInstanceOf(ThumbnailStorageUnavailableException.class);
    }
}
