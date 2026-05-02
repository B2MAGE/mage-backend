package com.bdmage.mage_backend.repository;

import java.util.List;
import java.util.Optional;

import com.bdmage.mage_backend.model.SceneComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SceneCommentRepository extends JpaRepository<SceneComment, Long> {

	@Query(value = """
			SELECT
			  sc.id AS "commentId",
			  sc.scene_id AS "sceneId",
			  sc.parent_comment_id AS "parentCommentId",
			  sc.user_id AS "authorUserId",
			  u.display_name AS "authorDisplayName",
			  sc.body AS "text",
			  sc.created_at AS "createdAt",
			  (SELECT COUNT(*) FROM scene_comments replies WHERE replies.parent_comment_id = sc.id) AS "replyCount",
			  (SELECT COUNT(*) FROM scene_comment_votes scv WHERE scv.comment_id = sc.id AND scv.vote_value = 1) AS upvotes,
			  (SELECT COUNT(*) FROM scene_comment_votes scv WHERE scv.comment_id = sc.id AND scv.vote_value = -1) AS downvotes,
			  (SELECT scv.vote_value FROM scene_comment_votes scv WHERE scv.comment_id = sc.id AND scv.user_id = :currentUserId) AS "currentUserVoteValue"
			FROM scene_comments sc
			JOIN users u ON u.id = sc.user_id
			WHERE sc.scene_id = :sceneId
			ORDER BY sc.created_at ASC, sc.id ASC
			""", nativeQuery = true)
	List<SceneCommentSummaryProjection> summarizeSceneComments(
			@Param("sceneId") Long sceneId,
			@Param("currentUserId") Long currentUserId);

	@Query(value = """
			SELECT
			  sc.id AS "commentId",
			  sc.scene_id AS "sceneId",
			  sc.parent_comment_id AS "parentCommentId",
			  sc.user_id AS "authorUserId",
			  u.display_name AS "authorDisplayName",
			  sc.body AS "text",
			  sc.created_at AS "createdAt",
			  (SELECT COUNT(*) FROM scene_comments replies WHERE replies.parent_comment_id = sc.id) AS "replyCount",
			  (SELECT COUNT(*) FROM scene_comment_votes scv WHERE scv.comment_id = sc.id AND scv.vote_value = 1) AS upvotes,
			  (SELECT COUNT(*) FROM scene_comment_votes scv WHERE scv.comment_id = sc.id AND scv.vote_value = -1) AS downvotes,
			  (SELECT scv.vote_value FROM scene_comment_votes scv WHERE scv.comment_id = sc.id AND scv.user_id = :currentUserId) AS "currentUserVoteValue"
			FROM scene_comments sc
			JOIN users u ON u.id = sc.user_id
			WHERE sc.scene_id = :sceneId
			  AND sc.id = :commentId
			""", nativeQuery = true)
	Optional<SceneCommentSummaryProjection> summarizeSceneComment(
			@Param("sceneId") Long sceneId,
			@Param("commentId") Long commentId,
			@Param("currentUserId") Long currentUserId);

	@Modifying
	@Query(value = """
			INSERT INTO scene_comment_votes (comment_id, user_id, vote_value)
			VALUES (:commentId, :userId, :voteValue)
			ON CONFLICT (comment_id, user_id)
			DO UPDATE SET
			  vote_value = EXCLUDED.vote_value,
			  updated_at = CURRENT_TIMESTAMP
			""", nativeQuery = true)
	void upsertCommentVote(
			@Param("commentId") Long commentId,
			@Param("userId") Long userId,
			@Param("voteValue") int voteValue);

	@Modifying
	@Query(value = """
			DELETE FROM scene_comment_votes
			WHERE comment_id = :commentId
			  AND user_id = :userId
			""", nativeQuery = true)
	void deleteCommentVote(@Param("commentId") Long commentId, @Param("userId") Long userId);
}
