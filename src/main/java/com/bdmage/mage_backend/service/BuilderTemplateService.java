package com.bdmage.mage_backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.bdmage.mage_backend.config.ThumbnailStorageProperties;
import com.bdmage.mage_backend.dto.BuilderTemplateResponse;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.BuilderTemplateException;
import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.repository.BuilderTemplateRepository;
import com.bdmage.mage_backend.validation.SceneDocumentValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuilderTemplateService {
    private final BuilderTemplateRepository templates;
    private final ThumbnailStorageService storage;
    private final ThumbnailStorageProperties properties;
    private final BuilderTemplateAssetCleanup cleanup;
    private final SceneDocumentValidator validator = new SceneDocumentValidator();

    public BuilderTemplateService(BuilderTemplateRepository templates,
            ThumbnailStorageService storage, ThumbnailStorageProperties properties, BuilderTemplateAssetCleanup cleanup) {
        this.templates = templates;
        this.storage = storage;
        this.properties = properties;
        this.cleanup = cleanup;
    }

    @Transactional(readOnly = true)
    public List<BuilderTemplateResponse> list(Long owner) {
        requireOwner(owner);
        return this.templates.list(owner);
    }

    @Transactional(readOnly = true)
    public BuilderTemplateResponse get(Long owner, Long id) { return owned(owner, id, false); }

    @Transactional
    public BuilderTemplateResponse create(Long owner, String name, JsonNode document, String upload) {
        requireOwner(owner);
        String normalizedName = name(name);
        JsonNode validated = document(document);
        BuilderTemplateResponse saved;
        try { saved = this.templates.create(owner, normalizedName, validated); }
        catch (DataIntegrityViolationException ex) { throw duplicateName(); }
        String thumbnail = thumbnail(owner, saved.templateId(), upload);
        return thumbnail == null ? saved : this.templates.setInitialThumbnail(saved.templateId(), thumbnail);
    }

    @Transactional
    public BuilderTemplateResponse update(Long owner, Long id, Long revision, String name, JsonNode document, String upload) {
        BuilderTemplateResponse current = owned(owner, id, true);
        checkRevision(current, revision);
        String normalizedName = name(name);
        JsonNode validated = document(document);
        String thumbnail = thumbnail(owner, id, upload);
        BuilderTemplateResponse saved;
        try { saved = this.templates.update(id, normalizedName, validated, thumbnail); }
        catch (DataIntegrityViolationException ex) { throw duplicateName(); }
        this.cleanup.enqueue(current.thumbnailRef(), Instant.now());
        return saved;
    }

    @Transactional
    public BuilderTemplateResponse rename(Long owner, Long id, Long revision, String name) {
        BuilderTemplateResponse current = owned(owner, id, true);
        checkRevision(current, revision);
        try { return this.templates.update(id, name(name), SceneService.sceneDataJson(current.sceneData()), current.thumbnailRef()); }
        catch (DataIntegrityViolationException ex) { throw duplicateName(); }
    }

    @Transactional
    public void delete(Long owner, Long id, Long revision) {
        BuilderTemplateResponse current = owned(owner, id, true);
        checkRevision(current, revision);
        this.templates.delete(id);
        this.cleanup.enqueue(current.thumbnailRef(), Instant.now());
    }

    @Transactional
    public ThumbnailStorageService.PresignedThumbnailUpload presign(Long owner, String filename, String type, Long bytes) {
        requireOwner(owner);
        if (filename == null || filename.isBlank() || bytes == null || bytes <= 0
                || bytes > this.properties.maxBytes() || !this.properties.allowedContentTypeSet().contains(type)) {
            throw new InvalidThumbnailException("Choose a nonempty image (jpeg, png, webp, or gif), at most 5 MB.");
        }
        var upload = this.storage.createTemplateUpload(owner, filename, type);
        this.templates.registerUpload(owner, upload.objectKey(), upload.expiresAt());
        // Keep the cleanup record beyond presign expiry so a late PUT cannot recreate an orphan.
        this.cleanup.enqueue(upload.objectKey(), upload.expiresAt().plusSeconds(300));
        return upload;
    }

    @Transactional
    public void discardUpload(Long owner, String objectKey) {
        requireOwner(owner);
        if (!this.templates.consumeUpload(owner, objectKey, false))
            throw new InvalidThumbnailException("Thumbnail upload key is invalid or already used.");
        // Expiry cleanup remains queued; immediate deletion alone would permit a late signed PUT to orphan an image.
    }

    private String thumbnail(Long owner, Long id, String upload) {
        if (upload == null) return null;
        if (upload.isBlank()) throw new InvalidThumbnailException("Thumbnail upload key must not be blank.");
        if (!this.templates.consumeUpload(owner, upload, true))
            throw new InvalidThumbnailException("Thumbnail upload expired, is invalid, or was already used. Capture it again.");
        String destination = this.properties.normalizedKeyPrefix() + "/builder-templates/saved/" + id + "/" + UUID.randomUUID();
        // This independent record survives rollback or interruption between the object copy and database commit.
        this.cleanup.trackCopy(this.properties.toPublicUrl(destination));
        return this.storage.copyTemplateUpload(owner, upload, destination).publicUrl();
    }

    private BuilderTemplateResponse owned(Long owner, Long id, boolean lock) {
        requireOwner(owner);
        return this.templates.find(owner, id, lock).orElseThrow(() -> new BuilderTemplateException(HttpStatus.NOT_FOUND,
                "BUILDER_TEMPLATE_NOT_FOUND", "Template not found."));
    }

    private static void requireOwner(Long owner) {
        if (owner == null) throw new AuthenticationRequiredException("Authentication is required.");
    }

    private static void checkRevision(BuilderTemplateResponse current, Long expected) {
        if (expected == null || expected < 0) throw new InvalidSceneDataException(Map.of("expectedRevision", "Expected a nonnegative integer revision."));
        if (current.revision() != expected) throw new BuilderTemplateException(HttpStatus.CONFLICT,
                "BUILDER_TEMPLATE_EDIT_CONFLICT", "This template has changed. Your draft is safe; reload the template before updating it.");
    }

    private static String name(String value) {
        String name = value == null ? "" : value.strip();
        if (name.isBlank() || name.length() > 100) throw new InvalidSceneDataException(Map.of("name", "Name must contain 1 to 100 characters."));
        return name;
    }

    private JsonNode document(JsonNode value) {
        if (value == null || !"builder".equals(value.path("kind").asText())) {
            throw new InvalidSceneDataException(Map.of("sceneData.kind", "Personal templates must contain a current Builder document."));
        }
        for (String field : List.of("parameters", "settings")) {
            if (value.has(field)) throw new InvalidSceneDataException(Map.of("sceneData." + field,
                    "Personal templates save the Builder design only. Scene settings and global parameters are not allowed."));
        }
        // Reuse scene validation and its resource budgets; scene-wide defaults stay out of template storage.
        ObjectNode normalized = (ObjectNode) this.validator.validateAndNormalize(value);
        normalized.remove(List.of("parameters", "settings"));
        return normalized;
    }

    private static BuilderTemplateException duplicateName() {
        return new BuilderTemplateException(HttpStatus.CONFLICT, "BUILDER_TEMPLATE_NAME_EXISTS", "You already have a template with this name. Choose another name.");
    }
}
