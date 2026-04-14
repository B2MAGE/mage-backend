package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Locale;

import com.bdmage.mage_backend.exception.TagAlreadyExistsException;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.repository.TagRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

	private static final String DUPLICATE_TAG_MESSAGE = "A tag with this name already exists.";

	private final TagRepository tagRepository;

	public TagService(TagRepository tagRepository) {
		this.tagRepository = tagRepository;
	}

	@Transactional
	public Tag createTag(String name) {
		String normalizedName = normalizeName(name);

		if (this.tagRepository.findByName(normalizedName).isPresent()) {
			throw new TagAlreadyExistsException(DUPLICATE_TAG_MESSAGE);
		}

		try {
			return this.tagRepository.saveAndFlush(new Tag(normalizedName));
		} catch (DataIntegrityViolationException ex) {
			throw new TagAlreadyExistsException(DUPLICATE_TAG_MESSAGE);
		}
	}

	@Transactional(readOnly = true)
	public List<Tag> getAllTags() {
		return this.tagRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
	}

	@Transactional(readOnly = true)
	public List<Tag> getAllTagsAttachedToPresets() {
		return this.tagRepository.findAllAttachedToPresets();
	}

	private static String normalizeName(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}
}
