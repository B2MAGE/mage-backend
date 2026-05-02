package com.bdmage.mage_backend.service;

import com.bdmage.mage_backend.dto.SceneEngagementResponse;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.SceneNotFoundException;
import com.bdmage.mage_backend.repository.SceneEngagementRepository;
import com.bdmage.mage_backend.repository.SceneEngagementSummaryProjection;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SceneEngagementService {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String SCENE_NOT_FOUND_MESSAGE = "Scene not found.";

	private final SceneEngagementRepository sceneEngagementRepository;
	private final SceneRepository sceneRepository;
	private final UserRepository userRepository;

	public SceneEngagementService(
			SceneEngagementRepository sceneEngagementRepository,
			SceneRepository sceneRepository,
			UserRepository userRepository) {
		this.sceneEngagementRepository = sceneEngagementRepository;
		this.sceneRepository = sceneRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public SceneEngagementResponse getSceneEngagement(Long sceneId, Long currentUserId) {
		SceneEngagementSummaryProjection summary = this.sceneEngagementRepository
				.summarizeSceneEngagement(sceneId, currentUserId);
		return summary != null ? SceneEngagementResponse.from(summary) : SceneEngagementResponse.empty();
	}

	@Transactional
	public SceneEngagementResponse recordSceneView(Long sceneId, Long currentUserId) {
		requireSceneExists(sceneId);
		this.sceneEngagementRepository.insertSceneView(sceneId, currentUserId);
		return getSceneEngagement(sceneId, currentUserId);
	}

	@Transactional
	public SceneEngagementResponse setSceneVote(Long sceneId, Long currentUserId, String vote) {
		requireAuthenticatedUser(currentUserId);
		requireSceneExists(sceneId);
		this.sceneEngagementRepository.upsertSceneVote(sceneId, currentUserId, voteValue(vote));
		return getSceneEngagement(sceneId, currentUserId);
	}

	@Transactional
	public SceneEngagementResponse clearSceneVote(Long sceneId, Long currentUserId) {
		requireAuthenticatedUser(currentUserId);
		requireSceneExists(sceneId);
		this.sceneEngagementRepository.deleteSceneVote(sceneId, currentUserId);
		return getSceneEngagement(sceneId, currentUserId);
	}

	@Transactional
	public SceneEngagementResponse saveScene(Long sceneId, Long currentUserId) {
		requireAuthenticatedUser(currentUserId);
		requireSceneExists(sceneId);
		this.sceneEngagementRepository.insertSceneSave(sceneId, currentUserId);
		return getSceneEngagement(sceneId, currentUserId);
	}

	@Transactional
	public SceneEngagementResponse unsaveScene(Long sceneId, Long currentUserId) {
		requireAuthenticatedUser(currentUserId);
		requireSceneExists(sceneId);
		this.sceneEngagementRepository.deleteSceneSave(sceneId, currentUserId);
		return getSceneEngagement(sceneId, currentUserId);
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

	private static int voteValue(String vote) {
		return switch (vote) {
			case "up" -> 1;
			case "down" -> -1;
			default -> throw new IllegalArgumentException("Unsupported scene vote: " + vote);
		};
	}
}
