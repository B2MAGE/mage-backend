package com.bdmage.mage_backend.validation;

import java.util.List;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuilderRenderingValidatorTests {

	private final ObjectMapper json = new ObjectMapper();
	private final SceneDocumentValidator validator = new SceneDocumentValidator();

	@Test
	void preservesOptionalRenderingAndAppliesDefaultsOnlyWhenItIsPresent() throws Exception {
		assertThat(this.validator.validateAndNormalize(builder()).has("rendering")).isFalse();
		ObjectNode document = builder();
		document.putObject("rendering");
		JsonNode defaults = this.validator.validateAndNormalize(document).path("rendering");
		assertThat(defaults.path("maxIterations").asInt()).isEqualTo(96);
		assertThat(defaults.path("stepSize").asDouble()).isEqualTo(0.7);
		ObjectNode rendering = document.putObject("rendering").put("maxIterations", 198).put("stepSize", 0.8838993805155669);
		JsonNode normalized = this.validator.validateAndNormalize(document);
		assertThat(normalized.path("rendering")).isEqualTo(rendering);
		assertThat(this.validator.validateAndNormalize(this.json.readTree(normalized.toString()))).isEqualTo(normalized);
	}

	@Test
	void enforcesTheFiniteRenderingCeilingsAndRejectsUnknownProfileFields() throws Exception {
		for (String profile : List.of("{\"maxIterations\":0}", "{\"maxIterations\":201}", "{\"maxIterations\":1.5}",
				"{\"stepSize\":0.0049}", "{\"stepSize\":1.001}", "{\"maxIterations\":\"198\"}", "{\"shader\":\"NEVER_EXECUTE()\"}")) {
			ObjectNode document = builder();
			document.set("rendering", this.json.readTree(profile));
			assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOf(InvalidSceneDataException.class);
		}
		for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
			ObjectNode document = builder();
			document.putObject("rendering").put("stepSize", value);
			assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOf(InvalidSceneDataException.class);
		}
		ObjectNode document = builder();
		document.putObject("rendering").put("maxIterations", 200).put("stepSize", 0.005);
		assertThat(this.validator.validateAndNormalize(document).path("rendering")).isEqualTo(document.path("rendering"));
	}

	private ObjectNode builder() {
		ObjectNode document = this.json.createObjectNode().put("schemaVersion", 1).put("kind", "builder").put("builderVersion", 1);
		document.putArray("objects").addObject().put("id", "shape").putObject("operation").put("type", "sphere");
		return document;
	}
}
