package com.bdmage.mage_backend.repository;

import com.bdmage.mage_backend.model.Scene;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SceneEngagementRepository extends Repository<Scene, Long> {

	@Query(value = """
			SELECT
			  (SELECT COUNT(*) FROM scene_views svw WHERE svw.scene_id = :sceneId) AS views,
			  (SELECT COUNT(*) FROM scene_votes sv WHERE sv.scene_id = :sceneId AND sv.vote_value = 1) AS upvotes,
			  (SELECT COUNT(*) FROM scene_votes sv WHERE sv.scene_id = :sceneId AND sv.vote_value = -1) AS downvotes,
			  (SELECT COUNT(*) FROM scene_saves ss WHERE ss.scene_id = :sceneId) AS saves,
			  (SELECT sv.vote_value FROM scene_votes sv WHERE sv.scene_id = :sceneId AND sv.user_id = :currentUserId) AS "currentUserVoteValue",
			  EXISTS(
			    SELECT 1
			    FROM scene_saves ss
			    WHERE ss.scene_id = :sceneId
			      AND ss.user_id = :currentUserId
			  ) AS "currentUserSaved"
			""", nativeQuery = true)
	SceneEngagementSummaryProjection summarizeSceneEngagement(
			@Param("sceneId") Long sceneId,
			@Param("currentUserId") Long currentUserId);

	@Modifying
	@Query(value = """
			INSERT INTO scene_views (scene_id, user_id)
			VALUES (:sceneId, :userId)
			""", nativeQuery = true)
	void insertSceneView(@Param("sceneId") Long sceneId, @Param("userId") Long userId);

	@Modifying
	@Query(value = """
			INSERT INTO scene_votes (scene_id, user_id, vote_value)
			VALUES (:sceneId, :userId, :voteValue)
			ON CONFLICT (scene_id, user_id)
			DO UPDATE SET
			  vote_value = EXCLUDED.vote_value,
			  updated_at = CURRENT_TIMESTAMP
			""", nativeQuery = true)
	void upsertSceneVote(
			@Param("sceneId") Long sceneId,
			@Param("userId") Long userId,
			@Param("voteValue") int voteValue);

	@Modifying
	@Query(value = """
			DELETE FROM scene_votes
			WHERE scene_id = :sceneId
			  AND user_id = :userId
			""", nativeQuery = true)
	void deleteSceneVote(@Param("sceneId") Long sceneId, @Param("userId") Long userId);

	@Modifying
	@Query(value = """
			INSERT INTO scene_saves (scene_id, user_id)
			VALUES (:sceneId, :userId)
			ON CONFLICT (scene_id, user_id) DO NOTHING
			""", nativeQuery = true)
	void insertSceneSave(@Param("sceneId") Long sceneId, @Param("userId") Long userId);

	@Modifying
	@Query(value = """
			DELETE FROM scene_saves
			WHERE scene_id = :sceneId
			  AND user_id = :userId
			""", nativeQuery = true)
	void deleteSceneSave(@Param("sceneId") Long sceneId, @Param("userId") Long userId);
}
