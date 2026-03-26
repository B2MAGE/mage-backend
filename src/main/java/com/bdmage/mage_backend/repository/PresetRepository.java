package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.Preset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresetRepository extends JpaRepository<Preset, Long> {

	List<Preset> findAllByOwnerUserId(Long ownerUserId);
}
