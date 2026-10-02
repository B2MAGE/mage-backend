package com.bdmage.mage_backend.repository;

import java.util.List;
import java.util.Optional;

import com.bdmage.mage_backend.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);

    @Query("""
            SELECT tag.id AS tagId, tag.name AS name, COUNT(DISTINCT sceneTag.sceneId) AS sceneCount
            FROM Tag tag
            LEFT JOIN SceneTag sceneTag ON sceneTag.tagId = tag.id
            GROUP BY tag.id, tag.name
            HAVING :attachedOnly = false OR COUNT(DISTINCT sceneTag.sceneId) > 0
            ORDER BY tag.name ASC
            """)
    List<TagUsageProjection> findAllWithSceneCounts(@Param("attachedOnly") boolean attachedOnly);
}
