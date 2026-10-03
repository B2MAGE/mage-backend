package com.bdmage.mage_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.CreateSceneCommentRequest;
import com.bdmage.mage_backend.dto.SceneCommentResponse;
import com.bdmage.mage_backend.dto.UpdateCommentVoteRequest;
import com.bdmage.mage_backend.service.SceneCommentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/scenes/{sceneId}/comments")
public class SceneCommentController {

	private final SceneCommentService sceneCommentService;

	public SceneCommentController(SceneCommentService sceneCommentService) {
		this.sceneCommentService = sceneCommentService;
	}

	@GetMapping
	ResponseEntity<List<SceneCommentResponse>> getSceneComments(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long sceneId,
			@RequestParam(required = false) String sort) {
		return ResponseEntity.ok(this.sceneCommentService.getSceneComments(sceneId, authenticatedUserId, sort));
	}

	@PostMapping
	ResponseEntity<SceneCommentResponse> createSceneComment(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long sceneId,
			@Valid @RequestBody CreateSceneCommentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(this.sceneCommentService.createSceneComment(
						sceneId,
						authenticatedUserId,
						request.text(),
						request.parentCommentId()));
	}

	@PutMapping("/{commentId}/vote")
	ResponseEntity<SceneCommentResponse> setCommentVote(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long sceneId,
			@PathVariable Long commentId,
			@Valid @RequestBody UpdateCommentVoteRequest request) {
		return ResponseEntity.ok(this.sceneCommentService.setCommentVote(
				sceneId,
				commentId,
				authenticatedUserId,
				request.vote()));
	}

	@DeleteMapping("/{commentId}/vote")
	ResponseEntity<SceneCommentResponse> clearCommentVote(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId,
			@PathVariable Long sceneId,
			@PathVariable Long commentId) {
		return ResponseEntity.ok(this.sceneCommentService.clearCommentVote(sceneId, commentId, authenticatedUserId));
	}
}
