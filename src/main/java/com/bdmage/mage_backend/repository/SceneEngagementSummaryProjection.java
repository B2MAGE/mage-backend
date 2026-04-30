package com.bdmage.mage_backend.repository;

public interface SceneEngagementSummaryProjection {

	Number getViews();

	Number getUpvotes();

	Number getDownvotes();

	Number getSaves();

	Number getCurrentUserVoteValue();

	Boolean getCurrentUserSaved();
}
