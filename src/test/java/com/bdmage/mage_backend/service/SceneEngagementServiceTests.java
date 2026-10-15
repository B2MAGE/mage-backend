package com.bdmage.mage_backend.service;

import java.util.List;

import com.bdmage.mage_backend.dto.SceneEngagementResponse;
import com.bdmage.mage_backend.repository.SceneEngagementRepository;
import com.bdmage.mage_backend.repository.SceneEngagementSummaryProjection;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SceneEngagementServiceTests {

	private final SceneEngagementRepository repository = mock(SceneEngagementRepository.class);
	private final SceneEngagementService service = new SceneEngagementService(
			repository, mock(SceneRepository.class), mock(UserRepository.class));

	@Test
	void emptyListsDoNotQueryTheDatabase() {
		assertThat(service.getSceneEngagements(List.of(), null)).isEmpty();
		verifyNoInteractions(repository);
	}

	@Test
	void individualReadsUseTheSameAggregatesAndRoundingAsCollections() {
		SceneEngagementSummaryProjection summary = mock(SceneEngagementSummaryProjection.class);
		when(summary.getSceneId()).thenReturn(9L);
		when(summary.getViews()).thenReturn(12L);
		when(summary.getUpvotes()).thenReturn(2L);
		when(summary.getDownvotes()).thenReturn(1L);
		when(summary.getComments()).thenReturn(5L);
		when(summary.getCurrentUserVoteValue()).thenReturn(-1);
		when(summary.getCurrentUserSaved()).thenReturn(true);
		when(repository.summarizeSceneEngagement(List.of(9L), 7L)).thenReturn(List.of(summary));

		assertThat(service.getSceneEngagement(9L, 7L))
				.isEqualTo(new SceneEngagementResponse(12, 2, 1, 0, 5, 67, "down", true));
	}

	@Test
	void missingOrEmptyEngagementHasDefinedZeroValues() {
		assertThat(service.getSceneEngagement(9L, null)).isEqualTo(SceneEngagementResponse.empty());
		assertThat(SceneEngagementResponse.from(mock(SceneEngagementSummaryProjection.class)))
				.isEqualTo(SceneEngagementResponse.empty());
	}
}
