package com.bdmage.mage_backend.validation;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentSceneRoundTripTests {

	private final ObjectMapper json = new ObjectMapper();
	private final SceneDocumentValidator validator = new SceneDocumentValidator();

	@TestFactory
	Stream<DynamicTest> currentWriteFixturesHaveMatchingDefaultsAndRejections() throws Exception {
		JsonNode fixtures;
		try (var input = getClass().getResourceAsStream("/contracts/scenes/current-round-trips.json")) {
			assertThat(input).isNotNull();
			fixtures = this.json.readTree(input);
		}
		assertThat(fixtures.path("cases")).isNotEmpty();
		assertThat(fixtures.path("cases").valueStream().filter(row -> row.path("valid").asBoolean())
				.map(row -> row.path("input").path("kind").asText()).collect(Collectors.toSet()))
				.isEqualTo(Set.of("template", "custom", "builder"));
		return fixtures.path("cases").valueStream().map(row -> DynamicTest.dynamicTest(row.path("name").asText(), () -> {
			JsonNode document = row.path("input");
			JsonNode original = document.deepCopy();
			if (row.path("valid").asBoolean()) {
				JsonNode normalized = this.validator.validateAndNormalize(document);
				assertThat(normalized).isEqualTo(row.path("normalized"));
				JsonNode reopened = this.json.readTree(this.json.writeValueAsBytes(normalized));
				assertThat(this.validator.validateAndNormalize(reopened)).isEqualTo(normalized);
			} else {
				assertThatThrownBy(() -> this.validator.validateAndNormalize(document))
						.isInstanceOf(InvalidSceneDataException.class);
			}
			assertThat(document).isEqualTo(original);
		}));
	}
}
