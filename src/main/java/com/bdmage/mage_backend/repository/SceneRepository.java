package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SceneRepository extends JpaRepository<Scene, Long> {

	@Override
	@Query("SELECT scene FROM Scene scene ORDER BY scene.id ASC")
	List<Scene> findAll();

	@Query(value = """
			SELECT p.*
			FROM scenes p
			JOIN scene_tags pt ON pt.scene_id = p.id
			JOIN tags t ON t.id = pt.tag_id
			WHERE t.name = :tagName
			ORDER BY p.id
			""", nativeQuery = true)
	List<Scene> findAllByTagName(@Param("tagName") String tagName);

	@Query("SELECT scene FROM Scene scene WHERE scene.ownerUserId = :ownerUserId ORDER BY scene.id ASC")
	List<Scene> findAllByOwnerUserId(@Param("ownerUserId") Long ownerUserId);
}
