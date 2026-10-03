package com.bdmage.mage_backend.config;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneAvailabilityPropertiesTests {

	@Test
	void omittedOperatorIdsAndReleaseApprovalFailClosed() {
		SceneAvailabilityProperties properties = new Binder(new MapConfigurationPropertySource(Map.of()))
				.bindOrCreate("mage.scene-availability", SceneAvailabilityProperties.class);
		assertThat(properties.operatorUserIds()).isEmpty();
		assertThat(properties.customRenderingReleaseApproved()).isFalse();
	}

	@Test
	void bindsExplicitAllowlistAndReleaseApproval() {
		SceneAvailabilityProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
				"mage.scene-availability.operator-user-ids", "7, 9",
				"mage.scene-availability.custom-rendering-release-approved", "true")))
				.bind("mage.scene-availability", SceneAvailabilityProperties.class).get();
		assertThat(properties.operatorUserIds()).containsExactlyInAnyOrder(7L, 9L);
		assertThat(properties.customRenderingReleaseApproved()).isTrue();
	}

	@Test
	void blankAllowlistRemainsEmpty() {
		SceneAvailabilityProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
				"mage.scene-availability.operator-user-ids", "")))
				.bind("mage.scene-availability", SceneAvailabilityProperties.class).get();
		assertThat(properties.operatorUserIds()).isEmpty();
	}

	@Test
	void rejectsNonPositiveOperatorIds() {
		assertThatThrownBy(() -> new SceneAvailabilityProperties(Set.of(0L), false))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
