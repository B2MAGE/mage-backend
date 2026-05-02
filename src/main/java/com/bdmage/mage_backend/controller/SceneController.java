package com.bdmage.mage_backend.controller;

import java.util.List;
import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.AttachTagToSceneRequest;
import com.bdmage.mage_backend.dto.CreateSceneRequest;
import com.bdmage.mage_backend.dto.CreateSceneThumbnailUploadRequest;
import com.bdmage.mage_backend.dto.FinalizeSceneThumbnailUploadRequest;
import com.bdmage.mage_backend.dto.SceneDetailResponse;
import com.bdmage.mage_backend.dto.SceneEngagementResponse;
import com.bdmage.mage_backend.dto.SceneResponse;
import com.bdmage.mage_backend.dto.SceneTagResponse;
import com.bdmage.mage_backend.dto.PresignedThumbnailUploadResponse;
import com.bdmage.mage_backend.dto.UpdateSceneDescriptionRequest;
import com.bdmage.mage_backend.dto.UpdateSceneRequest;
import com.bdmage.mage_backend.dto.UpdateSceneVoteRequest;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.service.SceneEngagementService;
import com.bdmage.mage_backend.service.SceneService;
import com.bdmage.mage_backend.service.SceneResponseFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
	private final SceneEngagementService sceneEngagementService;

	public SceneController(
			SceneService sceneService,
			SceneResponseFactory sceneResponseFactory,
			SceneEngagementService sceneEngagementService) {
		this.sceneService = sceneService;
		this.sceneResponseFactory = sceneResponseFactory;
		this.sceneEngagementService = sceneEngagementService;
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

	@PatchMapping("/{id}/description")
	ResponseEntity<SceneResponse> updateSceneDescription(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id,
			@Valid @RequestBody UpdateSceneDescriptionRequest request) {
		Scene scene = this.sceneService.updateDescription(authenticatedUserId, id, request.description());
		return ResponseEntity.ok(this.sceneResponseFactory.from(scene));
	}

	@PutMapping("/{id}")
	ResponseEntity<SceneResponse> updateScene(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id,
			@Valid @RequestBody UpdateSceneRequest request) {
		Scene scene = this.sceneService.updateScene(
				authenticatedUserId,
				id,
				request.name(),
				request.description(),
				SceneService.sceneDataJson(request.sceneData()));
		return ResponseEntity.ok(this.sceneResponseFactory.from(scene));
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
	ResponseEntity<List<SceneResponse>> getScenes(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@RequestParam(required = false) String tag) {
		List<Scene> scenes = StringUtils.hasText(tag)
				? this.sceneService.getScenesByTag(tag)
				: this.sceneService.getAllScenes();

		return ResponseEntity.ok(this.sceneResponseFactory.from(scenes, authenticatedUserId));
	}

	@GetMapping("/{id}")
	ResponseEntity<SceneDetailResponse> getScene(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id) {
		Scene scene = this.sceneService.getScene(id);
		List<String> tagNames = this.sceneService.getTagNamesForScene(id);

		return ResponseEntity.ok(this.sceneResponseFactory.detailFrom(
				scene,
				tagNames,
				this.sceneEngagementService.getSceneEngagement(id, authenticatedUserId)));
	}

	@PostMapping("/{id}/views")
	ResponseEntity<SceneEngagementResponse> recordSceneView(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id) {
		return ResponseEntity.ok(this.sceneEngagementService.recordSceneView(id, authenticatedUserId));
	}

	@PutMapping("/{id}/vote")
	ResponseEntity<SceneEngagementResponse> setSceneVote(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id,
			@Valid @RequestBody UpdateSceneVoteRequest request) {
		return ResponseEntity.ok(this.sceneEngagementService.setSceneVote(id, authenticatedUserId, request.vote()));
	}

	@DeleteMapping("/{id}/vote")
	ResponseEntity<SceneEngagementResponse> clearSceneVote(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id) {
		return ResponseEntity.ok(this.sceneEngagementService.clearSceneVote(id, authenticatedUserId));
	}

	@PostMapping("/{id}/save")
	ResponseEntity<SceneEngagementResponse> saveScene(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id) {
		return ResponseEntity.ok(this.sceneEngagementService.saveScene(id, authenticatedUserId));
	}

	@DeleteMapping("/{id}/save")
	ResponseEntity<SceneEngagementResponse> unsaveScene(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long id) {
		return ResponseEntity.ok(this.sceneEngagementService.unsaveScene(id, authenticatedUserId));
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
