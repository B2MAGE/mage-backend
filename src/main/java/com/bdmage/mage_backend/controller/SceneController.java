package com.bdmage.mage_backend.controller;

import java.util.List;
import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.AttachTagToSceneRequest;
import com.bdmage.mage_backend.dto.CreateSceneRequest;
import com.bdmage.mage_backend.dto.CreateSceneThumbnailUploadRequest;
import com.bdmage.mage_backend.dto.FinalizeSceneThumbnailUploadRequest;
import com.bdmage.mage_backend.dto.SceneResponse;
import com.bdmage.mage_backend.dto.SceneTagResponse;
import com.bdmage.mage_backend.dto.PresignedThumbnailUploadResponse;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.service.SceneService;
import com.bdmage.mage_backend.service.SceneResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenes")
public class SceneController {

	private final SceneService sceneService;
	private final SceneResponseFactory sceneResponseFactory;

	public SceneController(SceneService sceneService, SceneResponseFactory sceneResponseFactory) {
		this.sceneService = sceneService;
		this.sceneResponseFactory = sceneResponseFactory;
	}

	@PostMapping
	ResponseEntity<SceneResponse> createScene(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@Valid @RequestBody CreateSceneRequest request) {
		Scene scene = this.sceneService.createScene(
				authenticatedUserId,
				request.name(),
				request.description(),
				SceneService.sceneDataJson(request.sceneData()),
				request.thumbnailObjectKey());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.sceneResponseFactory.from(scene));
	}

	@PostMapping("/thumbnail/presign")
	ResponseEntity<PresignedThumbnailUploadResponse> createSceneThumbnailUpload(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@Valid @RequestBody CreateSceneThumbnailUploadRequest request) {
		return ResponseEntity.ok(PresignedThumbnailUploadResponse.from(this.sceneService.createSceneThumbnailUpload(
				authenticatedUserId,
				request.filename(),
				request.contentType(),
				request.sizeBytes())));
	}

	@PostMapping("/{id}/tags")
	ResponseEntity<SceneTagResponse> attachTagToScene(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id,
			@Valid @RequestBody AttachTagToSceneRequest request) {
		SceneTag sceneTag = this.sceneService.attachTagToScene(authenticatedUserId, id, request.tagId());

		return ResponseEntity.status(HttpStatus.CREATED)
				.body(SceneTagResponse.from(sceneTag));
	}

	@GetMapping
	ResponseEntity<List<SceneResponse>> getScenes(@RequestParam(required = false) String tag) {
		List<Scene> scenes = StringUtils.hasText(tag)
				? this.sceneService.getScenesByTag(tag)
				: this.sceneService.getAllScenes();

		return ResponseEntity.ok(this.sceneResponseFactory.from(scenes));
	}

	@GetMapping("/{id}")
	ResponseEntity<SceneResponse> getScene(@PathVariable Long id) {
		Scene scene = this.sceneService.getScene(id);
		List<String> tagNames = this.sceneService.getTagNamesForScene(id);

		return ResponseEntity.ok(this.sceneResponseFactory.from(scene, tagNames));
	}

	@PostMapping("/{id}/thumbnail/presign")
	ResponseEntity<PresignedThumbnailUploadResponse> createThumbnailUpload(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id,
			@Valid @RequestBody CreateSceneThumbnailUploadRequest request) {
		return ResponseEntity.ok(PresignedThumbnailUploadResponse.from(this.sceneService.createThumbnailUpload(
				authenticatedUserId,
				id,
				request.filename(),
				request.contentType(),
				request.sizeBytes())));
	}

	@PostMapping("/{id}/thumbnail/finalize")
	ResponseEntity<SceneResponse> finalizeThumbnailUpload(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id,
			@Valid @RequestBody FinalizeSceneThumbnailUploadRequest request) {
		Scene scene = this.sceneService.finalizeThumbnailUpload(authenticatedUserId, id, request.objectKey());
		return ResponseEntity.ok(this.sceneResponseFactory.from(scene));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> deleteScene(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id) {
		this.sceneService.deleteScene(authenticatedUserId, id);
		return ResponseEntity.noContent().build();
	}
}
