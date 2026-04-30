package com.bdmage.mage_backend.dto;

import com.bdmage.mage_backend.repository.SceneEngagementSummaryProjection;

public record SceneEngagementResponse(
		long views,
		long upvotes,
		long downvotes,
		long saves,
		String currentUserVote,
		boolean currentUserSaved) {

	public static SceneEngagementResponse from(SceneEngagementSummaryProjection summary) {
		return new SceneEngagementResponse(
				longValue(summary.getViews()),
				longValue(summary.getUpvotes()),
				longValue(summary.getDownvotes()),
				longValue(summary.getSaves()),
				currentUserVote(summary.getCurrentUserVoteValue()),
				Boolean.TRUE.equals(summary.getCurrentUserSaved()));
	}

	public static SceneEngagementResponse empty() {
		return new SceneEngagementResponse(0L, 0L, 0L, 0L, null, false);
	}

	private static long longValue(Number value) {
		return value == null ? 0L : value.longValue();
	}

	private static String currentUserVote(Number value) {
		if (value == null) {
			return null;
		}

		return switch (value.intValue()) {
			case 1 -> "up";
			case -1 -> "down";
			default -> null;
		};
	}
}
