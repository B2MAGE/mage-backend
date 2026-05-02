package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.bdmage.mage_backend.repository.SceneCommentSummaryProjection;

public record SceneCommentResponse(
		Long commentId,
		Long sceneId,
		Long parentCommentId,
		Long authorUserId,
		String authorDisplayName,
		String text,
		Instant createdAt,
		long replyCount,
		long upvotes,
		long downvotes,
		String currentUserVote,
		List<SceneCommentResponse> replies) {

	public static List<SceneCommentResponse> listFrom(List<SceneCommentSummaryProjection> summaries) {
		Map<Long, List<SceneCommentSummaryProjection>> repliesByParentId = summaries.stream()
				.filter(summary -> summary.getParentCommentId() != null)
				.collect(Collectors.groupingBy(SceneCommentSummaryProjection::getParentCommentId));

		return summaries.stream()
				.filter(summary -> summary.getParentCommentId() == null)
				.map(summary -> from(
						summary,
						repliesByParentId.getOrDefault(summary.getCommentId(), List.of()).stream()
								.map(reply -> from(reply, List.of()))
								.toList()))
				.toList();
	}

	public static SceneCommentResponse from(SceneCommentSummaryProjection summary) {
		return from(summary, List.of());
	}

	public static SceneCommentResponse from(
			SceneCommentSummaryProjection summary,
			List<SceneCommentResponse> replies) {
		return new SceneCommentResponse(
				summary.getCommentId(),
				summary.getSceneId(),
				summary.getParentCommentId(),
				summary.getAuthorUserId(),
				summary.getAuthorDisplayName(),
				summary.getText(),
				summary.getCreatedAt(),
				longValue(summary.getReplyCount()),
				longValue(summary.getUpvotes()),
				longValue(summary.getDownvotes()),
				currentUserVote(summary.getCurrentUserVoteValue()),
				List.copyOf(replies));
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
