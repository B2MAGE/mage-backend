package com.bdmage.mage_backend.dto;

import com.bdmage.mage_backend.model.Playlist;

public record PlaylistResponse(
		Long id,
		String name) {

	public static PlaylistResponse from(Playlist playlist) {
		return new PlaylistResponse(playlist.getId(), playlist.getName());
	}
}
