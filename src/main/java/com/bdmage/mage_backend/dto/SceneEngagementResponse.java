package com.bdmage.mage_backend.dto;

import com.bdmage.mage_backend.repository.SceneEngagementSummaryProjection;

public record SceneEngagementResponse(
		long views,
		long upvotes,
		long downvotes,
		long saves,
		long comments,
		int likesRatio,
		String currentUserVote,
		boolean currentUserSaved) {

	public static SceneEngagementResponse from(SceneEngagementSummaryProjection summary) {
		long upvotes = longValue(summary.getUpvotes());
		long downvotes = longValue(summary.getDownvotes());
		int likesRatio = upvotes == 0 && downvotes == 0 ? 0
				: (int) Math.round(100.0 * upvotes / ((double) upvotes + downvotes));
		return new SceneEngagementResponse(
				longValue(summary.getViews()),
				upvotes,
				downvotes,
				longValue(summary.getSaves()),
				longValue(summary.getComments()),
				likesRatio,
				currentUserVote(summary.getCurrentUserVoteValue()),
				Boolean.TRUE.equals(summary.getCurrentUserSaved()));
	}

	public static SceneEngagementResponse empty() {
		return new SceneEngagementResponse(0L, 0L, 0L, 0L, 0L, 0, null, false);
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
