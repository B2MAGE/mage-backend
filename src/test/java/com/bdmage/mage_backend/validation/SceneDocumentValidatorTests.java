package com.bdmage.mage_backend.validation;

import java.util.List;
import java.util.stream.Stream;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneDocumentValidatorTests {

	private final ObjectMapper mapper = new ObjectMapper();
	private final SceneDocumentValidator validator = new SceneDocumentValidator();

	@TestFactory
	Stream<DynamicTest> sharedFrontendFixturesHaveIdenticalValidityAndDefaults() throws Exception {
		JsonNode fixtures = resource("/contracts/scenes/fixtures.json");
		assertThat(fixtures.path("cases").size()).isEqualTo(84);
		return fixtures.path("cases").valueStream().map(row -> DynamicTest.dynamicTest(row.path("name").asText(), () -> {
			JsonNode document = row.path("document");
			JsonNode original = document.deepCopy();
			if (row.path("valid").booleanValue()) {
				JsonNode normalized = this.validator.validateContract(document);
				if (row.has("normalized")) assertThat(normalized).isEqualTo(row.path("normalized"));
				assertThat(normalized).isEqualTo(this.validator.validateContract(normalized));
			} else {
				assertThatThrownBy(() -> this.validator.validateContract(document)).isInstanceOf(InvalidSceneDataException.class);
			}
			assertThat(document).isEqualTo(original);
		}));
	}

	@Test
	void allCatalogPairsAreAcceptedAndOnlyCanonicalDataIsReturned() throws Exception {
		JsonNode catalog = resource("/contracts/scenes/template-catalog.v1.json");
		assertThat(catalog.path("templates")).hasSize(16);
		for (JsonNode row : catalog.path("templates")) {
			ObjectNode document = template();
			document.put("templateId", row.path("templateId").textValue());
			JsonNode normalized = this.validator.validateAndNormalize(document);
			assertThat(normalized.path("templateId")).isEqualTo(row.path("templateId"));
			assertThat(normalized.path("parameters").path("scale").intValue()).isEqualTo(10);
			assertThat(normalized.path("settings").path("camera").path("autoRotate").booleanValue()).isTrue();
			assertThat(normalized.has("source")).isFalse();
			assertThat(normalized.has("visualizer")).isFalse();
			assertThat(normalized.has("sourceSha256")).isFalse();
		}
	}

	@Test
	void allowsCustomSourceWithoutExecutingOrPromotingIt() {
		String source = "throw new Error('must never execute'); while(true) {}";
		ObjectNode document = custom(source);
		JsonNode result = this.validator.validateAndNormalize(document);
		assertThat(result).isEqualTo(document).isNotSameAs(document);
		assertThat(result.path("kind").textValue()).isEqualTo("custom");
		assertThat(result.path("scene")).isNotSameAs(document.path("scene"));
		assertThat(result.path("scene").path("visualizer").path("shader").textValue()).isEqualTo(source);
	}

	@Test
	void transportValidEmptyCustomStillFailsSubmissionFieldRequirements() {
		ObjectNode empty = this.mapper.createObjectNode().put("schemaVersion", 1).put("kind", "custom");
		empty.set("scene", this.mapper.createObjectNode());
		assertThatCode(() -> this.validator.validateContract(empty)).doesNotThrowAnyException();
		assertInvalid(empty, "sceneData.scene.visualizer");
	}

	@Test
	void rejectsRawLegacyAndServerOwnedMetadataWithPrecisePaths() {
		assertInvalid(this.mapper.createObjectNode().set("visualizer", this.mapper.createObjectNode().put("shader", "sphere(1);")), "sceneData.schemaVersion");
		for (String field : List.of("trusted", "mode", "validationVersion", "validatedAt", "sourceHash", "disabled", "sourceSha256")) {
			ObjectNode document = template().put(field, "client supplied");
			assertInvalid(document, "sceneData." + field);
		}
		ObjectNode mixed = template();
		mixed.set("scene", custom("sphere(1);").path("scene"));
		assertInvalid(mixed, "sceneData.scene");
	}

	@Test
	void rejectsNestedTemplateUnknownsWithoutDroppingThemOrMutatingInput() {
		for (String container : List.of("camera", "bloom", "tint")) {
			ObjectNode document = template();
			document.putObject("settings").putObject(container).put("expression", "doNotExecute()");
			JsonNode original = document.deepCopy();
			assertInvalid(document, "sceneData.settings." + container + ".expression");
			assertThat(document).isEqualTo(original);
		}
	}

	@Test
	void keepsSubmissionLimitsAndMapsCustomNestedFieldErrors() {
		ObjectNode document = custom("sphere(1);");
		((ObjectNode) document.path("scene")).putObject("intent").put("fov", 180);
		assertInvalid(document, "sceneData.scene.intent.fov");
		document = custom("a".repeat(SceneLimits.SOURCE_BYTES + 1));
		assertInvalid(document, "sceneData.scene.visualizer.shader");
		ObjectNode missingSource = custom("sphere(1);");
		((ObjectNode) missingSource.path("scene").path("visualizer")).remove("shader");
		assertInvalid(missingSource, "sceneData.scene.visualizer.shader");
	}

	@Test
	void keepsOptionalEffectBudgetForCustomDocuments() {
		ObjectNode document = custom("sphere(1);");
		ObjectNode fx = ((ObjectNode) document.path("scene")).putObject("fx");
		fx.putObject("bloom").put("enabled", true);
		fx.putObject("passes").put("rgbShift", true).put("afterImage", true).put("colorify", true).put("glitch", true);
		assertInvalid(document, "sceneData.scene.fx");
	}

	@Test
	void chargesEnvelopeBytesAgainstTheSameTotalBudget() throws Exception {
		int overhead = this.mapper.writeValueAsBytes(custom("")).length;
		int room = SceneLimits.SCENE_BYTES - overhead;
		String source = "\0".repeat(room / 6) + "a".repeat(room % 6);
		ObjectNode exact = custom(source);
		assertThat(this.mapper.writeValueAsBytes(exact)).hasSize(SceneLimits.SCENE_BYTES);
		this.validator.validateAndNormalize(exact);
		ObjectNode over = custom(source + "a");
		// The inner legacy scene still fits; the enclosing document does not.
		new SceneSubmissionValidator().validate(over.path("scene"));
		assertInvalid(over, "sceneData");
	}

	@Test
	void checksWholeEnvelopeDepthBeforeUnknownCustomFields() {
		ObjectNode document = custom("sphere(1);");
		ObjectNode inner = (ObjectNode) document.path("scene");
		for (int depth = 0; depth < SceneLimits.SCENE_DEPTH; depth++) inner = inner.putObject("nested");
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document))
				.isInstanceOfSatisfying(InvalidSceneDataException.class, exception -> {
					assertThat(exception.getDetails().keySet().iterator().next()).startsWith("sceneData.scene.nested");
					assertThat(exception.getDetails().values().iterator().next()).contains("nesting");
				});
	}

	@Test
	void rejectsPrototypeKeysNonJsonNodesAndNonFiniteValues() {
		for (String name : List.of("__proto__", "prototype", "constructor")) {
			ObjectNode document = custom("sphere(1);");
			((ObjectNode) document.path("scene")).putObject("nested").put(name, "value");
			assertInvalid(document, "sceneData.scene.nested." + name);
		}
		ObjectNode notJson = custom("sphere(1);");
		((ObjectNode) notJson.path("scene")).putPOJO("unexpected", new Object());
		assertInvalid(notJson, "sceneData.scene.unexpected");
		ObjectNode infinite = template();
		infinite.putObject("parameters").put("scale", Double.POSITIVE_INFINITY);
		assertInvalid(infinite, "sceneData.parameters.scale");
	}

	@Test
	void acceptsWholeNumberJsonSpellingWithoutCoercingStrings() {
		ObjectNode document = template().put("schemaVersion", 1.0).put("templateVersion", 1.0);
		document.putObject("settings").put("skybox", 6.0);
		assertThatCode(() -> this.validator.validateAndNormalize(document)).doesNotThrowAnyException();
		document.put("schemaVersion", "1");
		assertInvalid(document, "sceneData.schemaVersion");
	}

	private ObjectNode template() {
		return this.mapper.createObjectNode().put("schemaVersion", 1).put("kind", "template")
				.put("templateId", "embedded-scene-0").put("templateVersion", 1);
	}

	private ObjectNode custom(String source) {
		ObjectNode document = this.mapper.createObjectNode().put("schemaVersion", 1).put("kind", "custom");
		document.putObject("scene").putObject("visualizer").put("shader", source);
		return document;
	}

	private JsonNode resource(String path) throws Exception {
		try (var input = getClass().getResourceAsStream(path)) {
			assertThat(input).as(path).isNotNull();
			return this.mapper.readTree(input);
		}
	}

	private void assertInvalid(JsonNode document, String path) {
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document))
				.isInstanceOfSatisfying(InvalidSceneDataException.class, exception -> assertThat(exception.getDetails()).containsKey(path));
	}
}
