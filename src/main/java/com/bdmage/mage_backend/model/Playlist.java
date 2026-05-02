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
@Table(name = "playlists")
public class Playlist {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_user_id", nullable = false)
	private Long ownerUserId;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private Instant updatedAt;

	protected Playlist() {
	}

	public Playlist(Long ownerUserId, String name) {
		this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
		this.name = Objects.requireNonNull(name, "name must not be null").trim();
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

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}
}
