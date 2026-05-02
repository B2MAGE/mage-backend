package com.bdmage.mage_backend.repository;

import java.util.List;
import java.util.Optional;

import com.bdmage.mage_backend.model.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

	List<Playlist> findAllByOwnerUserIdOrderByNameAsc(Long ownerUserId);

	Optional<Playlist> findByIdAndOwnerUserId(Long playlistId, Long ownerUserId);

	boolean existsByIdAndOwnerUserId(Long playlistId, Long ownerUserId);
}
