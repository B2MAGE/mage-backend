package com.bdmage.mage_backend.service;

import java.util.Set;

import com.bdmage.mage_backend.config.SceneAvailabilityProperties;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.OperatorAccessRequiredException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorAccessServiceTests {

	@Test
	void deniesEveryoneWhenNoOperatorsAreConfigured() {
		OperatorAccessService service = new OperatorAccessService(new SceneAvailabilityProperties(null, false));
		assertThat(service.isOperator(1L)).isFalse();
		assertThat(service.isOperator(null)).isFalse();
		assertThatThrownBy(() -> service.requireOperator(1L)).isInstanceOf(OperatorAccessRequiredException.class);
	}

	@Test
	void distinguishesMissingAuthenticationFromAnAuthenticatedNonOperator() {
		OperatorAccessService service = new OperatorAccessService(new SceneAvailabilityProperties(Set.of(7L), false));
		assertThatThrownBy(() -> service.requireOperator(null)).isInstanceOf(AuthenticationRequiredException.class);
		assertThatThrownBy(() -> service.requireOperator(8L)).isInstanceOf(OperatorAccessRequiredException.class);
	}

	@Test
	void allowsOnlyExplicitlyConfiguredAuthenticatedIds() {
		OperatorAccessService service = new OperatorAccessService(new SceneAvailabilityProperties(Set.of(7L, 9L), false));
		assertThat(service.isOperator(7L)).isTrue();
		assertThatNoException().isThrownBy(() -> service.requireOperator(9L));
		assertThat(service.isOperator(8L)).isFalse();
	}
}
