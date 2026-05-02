package com.bdmage.mage_backend.model;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scene_comments")
public class SceneComment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "scene_id", nullable = false)
	private Long sceneId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "parent_comment_id")
	private Long parentCommentId;

	@Column(name = "body", nullable = false, length = 2000)
	private String body;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected SceneComment() {
	}

	public SceneComment(Long sceneId, Long userId, Long parentCommentId, String body) {
		this.sceneId = Objects.requireNonNull(sceneId, "sceneId must not be null");
		this.userId = Objects.requireNonNull(userId, "userId must not be null");
		this.parentCommentId = parentCommentId;
		this.body = Objects.requireNonNull(body, "body must not be null");
	}

	public Long getId() {
		return this.id;
	}

	public Long getSceneId() {
		return this.sceneId;
	}

	public Long getUserId() {
		return this.userId;
	}

	public Long getParentCommentId() {
		return this.parentCommentId;
	}

	public String getBody() {
		return this.body;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}
}
