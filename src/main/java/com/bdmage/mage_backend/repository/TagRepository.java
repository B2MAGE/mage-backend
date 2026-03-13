package com.bdmage.mage_backend.repository;

import java.util.Optional;

import com.bdmage.mage_backend.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);
}