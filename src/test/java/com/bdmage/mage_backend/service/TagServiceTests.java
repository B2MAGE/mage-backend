package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.exception.TagAlreadyExistsException;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagServiceTests {

	@Test
	void createTagNormalizesNameAndPersistsTag() {
		TagRepository tagRepository = mock(TagRepository.class);
		TagService tagService = new TagService(tagRepository);

		when(tagRepository.findByName("ambient")).thenReturn(Optional.empty());
		when(tagRepository.saveAndFlush(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0, Tag.class));

		Tag createdTag = tagService.createTag("  Ambient  ");

		ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
		verify(tagRepository).saveAndFlush(tagCaptor.capture());

		Tag savedTag = tagCaptor.getValue();
		assertThat(savedTag.getName()).isEqualTo("ambient");
		assertThat(createdTag.getName()).isEqualTo("ambient");
	}

	@Test
	void createTagRejectsDuplicateNormalizedName() {
		TagRepository tagRepository = mock(TagRepository.class);
		TagService tagService = new TagService(tagRepository);

		when(tagRepository.findByName("ambient")).thenReturn(Optional.of(new Tag("ambient")));

		assertThatThrownBy(() -> tagService.createTag("  Ambient  "))
				.isInstanceOf(TagAlreadyExistsException.class)
				.hasMessage("A tag with this name already exists.");

		verify(tagRepository).findByName("ambient");
		verify(tagRepository, never()).saveAndFlush(any(Tag.class));
	}

	@Test
	void createTagTranslatesDatabaseDuplicateViolations() {
		TagRepository tagRepository = mock(TagRepository.class);
		TagService tagService = new TagService(tagRepository);

		when(tagRepository.findByName("ambient")).thenReturn(Optional.empty());
		when(tagRepository.saveAndFlush(any(Tag.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> tagService.createTag("Ambient"))
				.isInstanceOf(TagAlreadyExistsException.class)
				.hasMessage("A tag with this name already exists.");
	}
}
