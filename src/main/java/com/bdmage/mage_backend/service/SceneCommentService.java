package com.bdmage.mage_backend.service;

import java.util.List;

import com.bdmage.mage_backend.dto.SceneCommentResponse;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidSceneCommentParentException;
import com.bdmage.mage_backend.exception.SceneCommentNotFoundException;
import com.bdmage.mage_backend.exception.SceneNotFoundException;
import com.bdmage.mage_backend.model.SceneComment;
import com.bdmage.mage_backend.repository.SceneCommentRepository;
import com.bdmage.mage_backend.repository.SceneCommentSummaryProjection;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SceneCommentService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String COMMENT_NOT_FOUND_MESSAGE = "Comment not found.";
	private static final String INVALID_PARENT_MESSAGE = "Parent comment must be a top-level comment on this scene.";
	private static final String SCENE_NOT_FOUND_MESSAGE = "Scene not found.";

	private final SceneCommentRepository sceneCommentRepository;
	private final SceneRepository sceneRepository;
	private final UserRepository userRepository;

	public SceneCommentService(
			SceneCommentRepository sceneCommentRepository,
			SceneRepository sceneRepository,
			UserRepository userRepository) {
		this.sceneCommentRepository = sceneCommentRepository;
		this.sceneRepository = sceneRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<SceneCommentResponse> getSceneComments(Long sceneId, Long currentUserId) {
		requireSceneExists(sceneId);
		return SceneCommentResponse.listFrom(
				this.sceneCommentRepository.summarizeSceneComments(sceneId, currentUserId));
	}

	@Transactional
	public SceneCommentResponse createSceneComment(
			Long sceneId,
			Long currentUserId,
			String text,
			Long parentCommentId) {
		requireAuthenticatedUser(currentUserId);
		requireSceneExists(sceneId);
		validateParentComment(sceneId, parentCommentId);

		SceneComment comment = this.sceneCommentRepository.save(new SceneComment(
				sceneId,
				currentUserId,
				parentCommentId,
				text.trim()));

		return summarizeSceneComment(sceneId, comment.getId(), currentUserId);
	}

	@Transactional
	public SceneCommentResponse setCommentVote(
			Long sceneId,
			Long commentId,
			Long currentUserId,
			String vote) {
		requireAuthenticatedUser(currentUserId);
		requireSceneExists(sceneId);
		requireCommentInScene(sceneId, commentId);

		this.sceneCommentRepository.upsertCommentVote(commentId, currentUserId, voteValue(vote));
		return summarizeSceneComment(sceneId, commentId, currentUserId);
	}

	@Transactional
	public SceneCommentResponse clearCommentVote(Long sceneId, Long commentId, Long currentUserId) {
		requireAuthenticatedUser(currentUserId);
		requireSceneExists(sceneId);
		requireCommentInScene(sceneId, commentId);

		this.sceneCommentRepository.deleteCommentVote(commentId, currentUserId);
		return summarizeSceneComment(sceneId, commentId, currentUserId);
	}

	private void requireAuthenticatedUser(Long currentUserId) {
		if (currentUserId == null || !this.userRepository.existsById(currentUserId)) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}
	}

	private void requireSceneExists(Long sceneId) {
		if (!this.sceneRepository.existsById(sceneId)) {
			throw new SceneNotFoundException(SCENE_NOT_FOUND_MESSAGE);
		}
	}

	private void requireCommentInScene(Long sceneId, Long commentId) {
		SceneComment comment = this.sceneCommentRepository.findById(commentId)
				.orElseThrow(() -> new SceneCommentNotFoundException(COMMENT_NOT_FOUND_MESSAGE));

		if (!sceneId.equals(comment.getSceneId())) {
			throw new SceneCommentNotFoundException(COMMENT_NOT_FOUND_MESSAGE);
		}
	}

	private void validateParentComment(Long sceneId, Long parentCommentId) {
		if (parentCommentId == null) {
			return;
		}

		SceneComment parentComment = this.sceneCommentRepository.findById(parentCommentId)
				.orElseThrow(() -> new InvalidSceneCommentParentException(INVALID_PARENT_MESSAGE));

		if (!sceneId.equals(parentComment.getSceneId()) || parentComment.getParentCommentId() != null) {
			throw new InvalidSceneCommentParentException(INVALID_PARENT_MESSAGE);
		}
	}

	private SceneCommentResponse summarizeSceneComment(Long sceneId, Long commentId, Long currentUserId) {
		SceneCommentSummaryProjection summary = this.sceneCommentRepository
				.summarizeSceneComment(sceneId, commentId, currentUserId)
				.orElseThrow(() -> new SceneCommentNotFoundException(COMMENT_NOT_FOUND_MESSAGE));

		return SceneCommentResponse.from(summary);
	}

	private static int voteValue(String vote) {
		return switch (vote) {
			case "up" -> 1;
			case "down" -> -1;
			default -> throw new IllegalArgumentException("Unsupported comment vote: " + vote);
		};
	}
}
