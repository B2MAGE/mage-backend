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
                FROM PresetTag presetTag
                WHERE presetTag.tagId = tag.id
            )
            ORDER BY tag.name ASC
            """)
    java.util.List<Tag> findAllAttachedToPresets();
}
