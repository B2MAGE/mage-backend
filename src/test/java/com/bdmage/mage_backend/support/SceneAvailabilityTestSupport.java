package com.bdmage.mage_backend.support;

import java.util.List;
import java.util.stream.Collectors;

import com.bdmage.mage_backend.dto.SceneAvailabilityResponse;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.service.SceneAvailabilityService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unrelated controller unit tests explicitly model an enabled playback policy. */
public final class SceneAvailabilityTestSupport {
	private SceneAvailabilityTestSupport() {}

	public static SceneAvailabilityService availableScenes() {
		SceneAvailabilityService availability = mock(SceneAvailabilityService.class);
		when(availability.status(any(Scene.class))).thenAnswer(invocation -> available(invocation.getArgument(0)));
		when(availability.statuses(anyList())).thenAnswer(invocation -> {
			List<Scene> scenes = invocation.getArgument(0);
			return scenes.stream().collect(Collectors.toMap(Scene::getId, SceneAvailabilityTestSupport::available));
		});
		return availability;
	}

	private static SceneAvailabilityResponse available(Scene scene) {
		return new SceneAvailabilityResponse(scene.getId(), true, "AVAILABLE", "Scene is available.");
	}
}
