package com.bdmage.mage_backend.repository;

import java.util.Optional;

import com.bdmage.mage_backend.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);

    @Query("""
            SELECT tag
            FROM Tag tag
            WHERE EXISTS (
                SELECT 1
                FROM SceneTag sceneTag
                WHERE sceneTag.tagId = tag.id
            )
            ORDER BY tag.name ASC
            """)
    java.util.List<Tag> findAllAttachedToScenes();
}
