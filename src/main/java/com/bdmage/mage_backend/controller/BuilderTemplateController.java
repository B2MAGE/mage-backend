package com.bdmage.mage_backend.controller;

import java.util.List;
import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.*;
import com.bdmage.mage_backend.service.BuilderTemplateService;
import com.bdmage.mage_backend.service.SceneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/builder-templates")
public class BuilderTemplateController {
    private final BuilderTemplateService service;
    public BuilderTemplateController(BuilderTemplateService service) { this.service = service; }

    @ModelAttribute
    void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
    }

    @GetMapping
    List<BuilderTemplateResponse> list(@RequestAttribute(AuthenticatedUserRequest.USER_ID_ATTRIBUTE) Long owner) {
        return this.service.list(owner);
    }

    @GetMapping("/{id}")
    BuilderTemplateResponse get(@RequestAttribute(AuthenticatedUserRequest.USER_ID_ATTRIBUTE) Long owner, @PathVariable Long id) {
        return this.service.get(owner, id);
    }

    @PostMapping
    ResponseEntity<BuilderTemplateResponse> create(@RequestAttribute(AuthenticatedUserRequest.USER_ID_ATTRIBUTE) Long owner,
            @Valid @RequestBody CreateBuilderTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.service.create(owner, request.name(),
                SceneService.sceneDataJson(request.sceneData()), request.thumbnailObjectKey()));
    }

    @PutMapping("/{id}")
    BuilderTemplateResponse update(@RequestAttribute(AuthenticatedUserRequest.USER_ID_ATTRIBUTE) Long owner, @PathVariable Long id,
            @Valid @RequestBody UpdateBuilderTemplateRequest request) {
        return this.service.update(owner, id, request.expectedRevision(), request.name(),
                SceneService.sceneDataJson(request.sceneData()), request.thumbnailObjectKey());
    }

    @PatchMapping("/{id}/name")
    BuilderTemplateResponse rename(@RequestAttribute(AuthenticatedUserRequest.USER_ID_ATTRIBUTE) Long owner, @PathVariable Long id,
            @Valid @RequestBody RenameBuilderTemplateRequest request) {
        return this.service.rename(owner, id, request.expectedRevision(), request.name());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@RequestAttribute(AuthenticatedUserRequest.USER_ID_ATTRIBUTE) Long owner, @PathVariable Long id,
            @RequestParam(required = false) Long expectedRevision) {
        this.service.delete(owner, id, expectedRevision);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/thumbnail/presign")
    PresignedThumbnailUploadResponse presign(@RequestAttribute(AuthenticatedUserRequest.USER_ID_ATTRIBUTE) Long owner,
            @Valid @RequestBody CreateSceneThumbnailUploadRequest request) {
        return PresignedThumbnailUploadResponse.from(this.service.presign(owner, request.filename(), request.contentType(), request.sizeBytes()));
    }

    @DeleteMapping("/thumbnail/pending")
    ResponseEntity<Void> discardUpload(@RequestAttribute(AuthenticatedUserRequest.USER_ID_ATTRIBUTE) Long owner, @RequestParam String objectKey) {
        this.service.discardUpload(owner, objectKey);
        return ResponseEntity.noContent().build();
    }
}
