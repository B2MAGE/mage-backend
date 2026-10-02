package com.bdmage.mage_backend.repository;

import java.util.List;
import java.util.Collection;

import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.model.SceneTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SceneTagRepository extends JpaRepository<SceneTag, SceneTagId> {

	@Query("""
			SELECT sceneTag.sceneId AS sceneId, tag.name AS tagName
			FROM SceneTag sceneTag, Tag tag
			WHERE sceneTag.tagId = tag.id AND sceneTag.sceneId IN :sceneIds
			ORDER BY tag.name ASC
			""")
	List<SceneTagNameProjection> findTagNamesBySceneIds(@Param("sceneIds") Collection<Long> sceneIds);

	List<SceneTag> findAllBySceneId(Long sceneId);

	List<SceneTag> findAllByTagId(Long tagId);

	boolean existsBySceneIdAndTagId(Long sceneId, Long tagId);
}
