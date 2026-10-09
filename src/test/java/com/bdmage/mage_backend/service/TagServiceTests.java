package com.bdmage.mage_backend.service;

import java.util.Optional;

import com.bdmage.mage_backend.dto.TagResponse;
import com.bdmage.mage_backend.exception.TagAlreadyExistsException;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.repository.TagRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.repository.TagUsageProjection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class TagServiceTests {

	@Test
	void createTagRequiresAnExistingAuthenticatedUserBeforeWriting() {
		TagRepository tags = mock(TagRepository.class);
		UserRepository users = mock(UserRepository.class);
		TagService service = new TagService(tags, users);
		assertThatThrownBy(() -> service.createTag(null, "ambient"))
				.isInstanceOf(AuthenticationRequiredException.class);
		assertThatThrownBy(() -> service.createTag(42L, "ambient"))
				.isInstanceOf(AuthenticationRequiredException.class);
		verifyNoInteractions(tags);
	}

	@Test
	void createTagNormalizesNameAndPersistsTag() {
		TagRepository tagRepository = mock(TagRepository.class);
		UserRepository users = mock(UserRepository.class);
		when(users.existsById(42L)).thenReturn(true);
		TagService tagService = new TagService(tagRepository, users);

		when(tagRepository.findByName("ambient")).thenReturn(Optional.empty());
		when(tagRepository.saveAndFlush(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0, Tag.class));

		Tag createdTag = tagService.createTag(42L, "  Ambient  ");

		ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
		verify(tagRepository).saveAndFlush(tagCaptor.capture());

		Tag savedTag = tagCaptor.getValue();
		assertThat(savedTag.getName()).isEqualTo("ambient");
		assertThat(createdTag.getName()).isEqualTo("ambient");
	}

	@Test
	void createTagRejectsDuplicateNormalizedName() {
		TagRepository tagRepository = mock(TagRepository.class);
		UserRepository users = mock(UserRepository.class);
		when(users.existsById(42L)).thenReturn(true);
		TagService tagService = new TagService(tagRepository, users);

		when(tagRepository.findByName("ambient")).thenReturn(Optional.of(new Tag("ambient")));

		assertThatThrownBy(() -> tagService.createTag(42L, "  Ambient  "))
				.isInstanceOf(TagAlreadyExistsException.class)
				.hasMessage("A tag with this name already exists.");

		verify(tagRepository).findByName("ambient");
		verify(tagRepository, never()).saveAndFlush(any(Tag.class));
	}

	@Test
	void createTagTranslatesDatabaseDuplicateViolations() {
		TagRepository tagRepository = mock(TagRepository.class);
		UserRepository users = mock(UserRepository.class);
		when(users.existsById(42L)).thenReturn(true);
		TagService tagService = new TagService(tagRepository, users);

		when(tagRepository.findByName("ambient")).thenReturn(Optional.empty());
		when(tagRepository.saveAndFlush(any(Tag.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> tagService.createTag(42L, "Ambient"))
				.isInstanceOf(TagAlreadyExistsException.class)
				.hasMessage("A tag with this name already exists.");
	}

	@Test
	void getTagsMapsSceneCountsWithoutChangingRepositoryOrder() {
		TagRepository tagRepository = mock(TagRepository.class);
		UserRepository users = mock(UserRepository.class);
		when(users.existsById(42L)).thenReturn(true);
		TagService tagService = new TagService(tagRepository, users);
		TagUsageProjection ambient = usage(15L, "ambient", 8);
		TagUsageProjection unused = usage(16L, "unused", 0);

		when(tagRepository.findAllWithSceneCounts(false)).thenReturn(java.util.List.of(ambient, unused));

		assertThat(tagService.getTags(false)).containsExactly(
				new TagResponse(15L, "ambient", 8), new TagResponse(16L, "unused", 0));
		verify(tagRepository).findAllWithSceneCounts(false);
	}

	@Test
	void getAttachedTagsUsesFilteredAggregateQuery() {
		TagRepository tagRepository = mock(TagRepository.class);
		UserRepository users = mock(UserRepository.class);
		when(users.existsById(42L)).thenReturn(true);
		TagService tagService = new TagService(tagRepository, users);
		TagUsageProjection ambient = usage(15L, "ambient", 8);

		when(tagRepository.findAllWithSceneCounts(true)).thenReturn(java.util.List.of(ambient));

		assertThat(tagService.getTags(true)).containsExactly(new TagResponse(15L, "ambient", 8));
		verify(tagRepository).findAllWithSceneCounts(true);
	}

	private static TagUsageProjection usage(Long id, String name, long sceneCount) {
		TagUsageProjection usage = mock(TagUsageProjection.class);
		when(usage.getTagId()).thenReturn(id);
		when(usage.getName()).thenReturn(name);
		when(usage.getSceneCount()).thenReturn(sceneCount);
		return usage;
	}
}
