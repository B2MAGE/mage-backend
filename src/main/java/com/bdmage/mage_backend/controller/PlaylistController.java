package com.bdmage.mage_backend.controller;

import java.util.List;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.PlaylistResponse;
import com.bdmage.mage_backend.service.PlaylistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

	private final PlaylistService playlistService;

	public PlaylistController(PlaylistService playlistService) {
		this.playlistService = playlistService;
	}

	@GetMapping
	ResponseEntity<List<PlaylistResponse>> getPlaylists(
			@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long authenticatedUserId) {
		return ResponseEntity.ok(this.playlistService.getPlaylistsForUser(authenticatedUserId).stream()
				.map(PlaylistResponse::from)
				.toList());
	}
}
