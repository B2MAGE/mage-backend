package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Set;

import com.bdmage.mage_backend.dto.SceneResponse;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.SceneTagNameProjection;
import com.bdmage.mage_backend.repository.SceneTagRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SceneResponseFactoryTests {

	private final UserRepository users = mock(UserRepository.class);
	private final SceneTagRepository tags = mock(SceneTagRepository.class);
	private final SceneEngagementService engagement = mock(SceneEngagementService.class);
	private final SceneResponseFactory factory = new SceneResponseFactory(users, engagement, tags);

	@Test
	void sceneListsIncludeOnlyTheirOwnTagsFromOneBatchLookup() {
		User owner = new User("ari@example.com", "hash", "Ari Rivera");
		ReflectionTestUtils.setField(owner, "id", 7L);
		when(users.findAllById(Set.of(7L))).thenReturn(List.of(owner));
		when(tags.findTagNamesBySceneIds(List.of(11L, 12L, 13L))).thenReturn(List.of(
				tag(11L, "ambient"), tag(11L, "neon"), tag(12L, "geometry")));

		List<SceneResponse> response = factory.from(List.of(scene(11L), scene(12L), scene(13L)));

		assertThat(response.get(0).tags()).containsExactly("ambient", "neon");
		assertThat(response.get(1).tags()).containsExactly("geometry");
		assertThat(response.get(2).tags()).isEmpty();
		assertThat(response).extracting(SceneResponse::creatorDisplayName).containsOnly("Ari Rivera");
		verify(tags).findTagNamesBySceneIds(List.of(11L, 12L, 13L));
	}

	@Test
	void emptyListsDoNotIssueTagOrOwnerQueries() {
		assertThat(factory.from(List.of())).isEmpty();
		verifyNoInteractions(users, tags, engagement);
	}

	private static Scene scene(Long id) {
		Scene scene = new Scene(7L, "Scene " + id, new ObjectMapper().createObjectNode());
		ReflectionTestUtils.setField(scene, "id", id);
		return scene;
	}

	private static SceneTagNameProjection tag(Long sceneId, String name) {
		return new SceneTagNameProjection() {
			public Long getSceneId() { return sceneId; }
			public String getTagName() { return name; }
		};
	}
}
