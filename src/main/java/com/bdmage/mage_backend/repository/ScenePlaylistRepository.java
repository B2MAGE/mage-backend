package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.ScenePlaylist;
import com.bdmage.mage_backend.model.ScenePlaylistId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenePlaylistRepository extends JpaRepository<ScenePlaylist, ScenePlaylistId> {

	List<ScenePlaylist> findAllBySceneId(Long sceneId);

	boolean existsBySceneIdAndPlaylistId(Long sceneId, Long playlistId);

	long countBySceneIdAndPlaylistId(Long sceneId, Long playlistId);
}
