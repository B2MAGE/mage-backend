package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.model.PresetTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresetTagRepository extends JpaRepository<PresetTag, PresetTagId> {

	List<PresetTag> findAllByPresetId(Long presetId);

	List<PresetTag> findAllByTagId(Long tagId);

	boolean existsByPresetIdAndTagId(Long presetId, Long tagId);
}
