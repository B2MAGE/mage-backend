package com.bdmage.mage_backend.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAvatarGradientTests {

	@Test
	void newLocalGoogleAndLinkedAccountsShareTheDefaultGradient() {
		for (User user : new User[] {
				new User("local@example.com", "hash", "Local"),
				User.google("google@example.com", "subject", "Google"),
				User.localAndGoogle("linked@example.com", "hash", "subject", "Linked") }) {
			assertThat(user.getAvatarGradientStart()).isEqualTo("#5c51ba");
			assertThat(user.getAvatarGradientEnd()).isEqualTo("#264a48");
		}
	}

	@Test
	void colorsAreNormalizedAndMissingValuesPreserveCurrentPreferences() {
		User user = new User("local@example.com", "hash", "Local");
		user.updateAvatarGradient("#ABCDEF", "#1234AB");
		assertThat(user.getAvatarGradientStart()).isEqualTo("#abcdef");
		assertThat(user.getAvatarGradientEnd()).isEqualTo("#1234ab");
		user.updateAvatarGradient(null, null);
		assertThat(user.getAvatarGradientStart()).isEqualTo("#abcdef");
		assertThat(user.getAvatarGradientEnd()).isEqualTo("#1234ab");
		user.updateAvatarGradient("#112233", null);
		assertThat(user.getAvatarGradientStart()).isEqualTo("#112233");
		assertThat(user.getAvatarGradientEnd()).isEqualTo("#1234ab");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "#123", "123456", "#12345678", "red", "#gg0000", " #123456", "url(test)"})
	void invalidColorsCannotChangeEitherEndpoint(String invalid) {
		User user = new User("local@example.com", "hash", "Local");
		assertThatThrownBy(() -> user.updateAvatarGradient("#abcdef", invalid))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(user.getAvatarGradientStart()).isEqualTo("#5c51ba");
		assertThat(user.getAvatarGradientEnd()).isEqualTo("#264a48");
	}
}
