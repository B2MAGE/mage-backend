package com.bdmage.mage_backend.repository;

public interface SceneEngagementSummaryProjection {

	Long getSceneId();

	Number getViews();

	Number getUpvotes();

	Number getDownvotes();

	Number getSaves();

	Number getComments();

	Number getCurrentUserVoteValue();

	Boolean getCurrentUserSaved();
}
