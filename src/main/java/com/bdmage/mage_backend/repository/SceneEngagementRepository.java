package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.Scene;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SceneEngagementRepository extends Repository<Scene, Long> {

	@Query(value = """
			WITH view_counts AS (
			  SELECT scene_id, COUNT(*) AS views
			  FROM scene_views WHERE scene_id IN (:sceneIds) GROUP BY scene_id
			), vote_counts AS (
			  SELECT scene_id,
			    COUNT(*) FILTER (WHERE vote_value = 1) AS upvotes,
			    COUNT(*) FILTER (WHERE vote_value = -1) AS downvotes,
			    MAX(CASE WHEN user_id = :currentUserId THEN vote_value END) AS current_user_vote
			  FROM scene_votes WHERE scene_id IN (:sceneIds) GROUP BY scene_id
			), save_counts AS (
			  SELECT scene_id, COUNT(*) AS saves,
			    BOOL_OR(user_id = :currentUserId) AS current_user_saved
			  FROM scene_saves WHERE scene_id IN (:sceneIds) GROUP BY scene_id
			), comment_counts AS (
			  SELECT scene_id, COUNT(*) AS comments
			  FROM scene_comments WHERE scene_id IN (:sceneIds) GROUP BY scene_id
			)
			SELECT s.id AS "sceneId",
			  COALESCE(v.views, 0) AS views,
			  COALESCE(vt.upvotes, 0) AS upvotes,
			  COALESCE(vt.downvotes, 0) AS downvotes,
			  COALESCE(sv.saves, 0) AS saves,
			  COALESCE(c.comments, 0) AS comments,
			  vt.current_user_vote AS "currentUserVoteValue",
			  COALESCE(sv.current_user_saved, FALSE) AS "currentUserSaved"
			FROM scenes s
			LEFT JOIN view_counts v ON v.scene_id = s.id
			LEFT JOIN vote_counts vt ON vt.scene_id = s.id
			LEFT JOIN save_counts sv ON sv.scene_id = s.id
			LEFT JOIN comment_counts c ON c.scene_id = s.id
			WHERE s.id IN (:sceneIds)
			""", nativeQuery = true)
	List<SceneEngagementSummaryProjection> summarizeSceneEngagement(
			@Param("sceneIds") List<Long> sceneIds,
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
