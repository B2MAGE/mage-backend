package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.model.SceneTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SceneTagRepository extends JpaRepository<SceneTag, SceneTagId> {

	List<SceneTag> findAllBySceneId(Long sceneId);

	List<SceneTag> findAllByTagId(Long tagId);

	boolean existsBySceneIdAndTagId(Long sceneId, Long tagId);
}
