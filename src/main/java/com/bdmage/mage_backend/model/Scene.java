package com.bdmage.mage_backend.model;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scenes")
public class Scene {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_user_id", nullable = false)
	private Long ownerUserId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 1000)
	private String description;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "scene_data", nullable = false, columnDefinition = "jsonb")
	private JsonNode sceneData;

	@Column(name = "thumbnail_ref", length = 512)
	private String thumbnailRef;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected Scene() {
	}

	public Scene(Long ownerUserId, String name, JsonNode sceneData) {
		this(ownerUserId, name, null, sceneData, null);
	}

	public Scene(Long ownerUserId, String name, JsonNode sceneData, String thumbnailRef) {
		this(ownerUserId, name, null, sceneData, thumbnailRef);
	}

	public Scene(Long ownerUserId, String name, String description, JsonNode sceneData) {
		this(ownerUserId, name, description, sceneData, null);
	}

	public Scene(Long ownerUserId, String name, String description, JsonNode sceneData, String thumbnailRef) {
		this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
		this.name = Objects.requireNonNull(name, "name must not be null");
		this.description = description;
		this.sceneData = Objects.requireNonNull(sceneData, "sceneData must not be null");
		this.thumbnailRef = thumbnailRef;
	}

	public Long getId() {
		return this.id;
	}

	public Long getOwnerUserId() {
		return this.ownerUserId;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public JsonNode getSceneData() {
		return this.sceneData;
	}

	public String getThumbnailRef() {
		return this.thumbnailRef;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void updateThumbnailRef(String thumbnailRef) {
		this.thumbnailRef = thumbnailRef;
	}

	public void updateDescription(String description) {
		this.description = description;
	}
}
