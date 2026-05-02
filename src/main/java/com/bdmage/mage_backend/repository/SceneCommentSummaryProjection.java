package com.bdmage.mage_backend.repository;

import java.time.Instant;

public interface SceneCommentSummaryProjection {

	Long getCommentId();

	Long getSceneId();

	Long getParentCommentId();

	Long getAuthorUserId();

	String getAuthorDisplayName();

	String getText();

	Instant getCreatedAt();

	Number getReplyCount();

	Number getUpvotes();

	Number getDownvotes();

	Number getCurrentUserVoteValue();
}
