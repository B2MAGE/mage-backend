package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.Preset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresetRepository extends JpaRepository<Preset, Long> {

	@Query(value = """
			SELECT p.*
			FROM presets p
			JOIN preset_tags pt ON pt.preset_id = p.id
			JOIN tags t ON t.id = pt.tag_id
			WHERE t.name = :tagName
			ORDER BY p.id
			""", nativeQuery = true)
	List<Preset> findAllByTagName(@Param("tagName") String tagName);

	List<Preset> findAllByOwnerUserId(Long ownerUserId);
}
