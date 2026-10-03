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
		assertThat(fixtures.path("cases").size()).isGreaterThanOrEqualTo(84);
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
	void restoredTemplateControlsStayDataOnlyAndRetainTheirValues() throws Exception {
		ObjectNode document = template();
		document.set("parameters", this.mapper.readTree("{\"scale\":200,\"speed\":10}"));
		JsonNode settings = this.mapper.readTree("""
				{"camera":{"fov":140,"orbitSpeed":-3,"tilt":0.4,"orientationMode":2,"orientationSpeed":4},
				 "controls":{"position0":{"x":1,"y":2,"z":7},"target0":{"x":0,"y":1,"z":0},"zoom0":2},
				 "motion":{"minimizing_factor":0.6,"power_factor":6,"pointerDownMultiplier":2,"base_speed":0.3,"easing_speed":0.4},
				 "state":{"size":2,"pointerDown":0.3,"currPointerDown":0.1,"currAudio":1,"time":12,"volume_multiplier":0.4},
				 "effects":{"passes":{"rgbShift":true,"afterImage":true,"kaleid":true},
				   "toneMapping":{"method":4,"exposure":1.2},"passOrder":["kaleidoShader","RGBShift","afterImagePass","outputPass"],
				   "params":{"rgbShift":{"amount":0.025,"angle":0.4},"afterImage":{"damp":0.8},"kaleid":{"sides":8,"angle":0.2}}},
				 "audioResponse":"mapped-v1","audioResponseConfig":{"version":1,"sensitivity":0.8,
				   "mappings":[{"target":"size","source":"bass-hit","amount":0.7,"attack":0.03,"release":0.4}]}}
				""");
		document.set("settings", settings);
		JsonNode normalized = this.validator.validateAndNormalize(document);
		for (String field : List.of("controls", "motion", "state", "effects", "audioResponse", "audioResponseConfig")) {
			assertThat(normalized.path("settings").path(field)).isEqualTo(settings.path(field));
		}
		assertThat(normalized.path("parameters")).isEqualTo(document.path("parameters"));
		assertThat(normalized.findValues("shader")).isEmpty();
		assertThat(normalized.path("kind").asText()).isEqualTo("template");
	}

	@Test
	void templateEffectBudgetIncludesBloomAndTintButExcludesOutput() {
		ObjectNode document = template();
		ObjectNode settings = document.putObject("settings");
		settings.putObject("bloom").put("enabled", true);
		settings.putObject("tint").put("enabled", true);
		ObjectNode passes = settings.putObject("effects").putObject("passes");
		passes.put("rgbShift", true).put("afterImage", true).put("outputPass", true);
		assertThatCode(() -> this.validator.validateAndNormalize(document)).doesNotThrowAnyException();
		passes.put("glitch", true);
		assertInvalid(document, "sceneData.settings.effects");
	}

	@Test
	void extendedTemplateControlsRejectSourceUnknownsDuplicatesAndOutOfRangeValues() throws Exception {
		for (String field : List.of("motion", "effects", "controls", "state", "audioResponseConfig")) {
			ObjectNode document = template();
			ObjectNode section = document.putObject("settings").putObject(field).put("shader", "sphere(1)");
			if (field.equals("audioResponseConfig")) section.put("version", 1);
			if (field.equals("controls")) {
				section.putObject("position0").put("x", 0).put("y", 0).put("z", 5);
				section.putObject("target0").put("x", 0).put("y", 0).put("z", 0);
				section.put("zoom0", 1);
			}
			assertInvalid(document, "sceneData.settings." + field + ".shader");
		}
		ObjectNode duplicatePass = template();
		duplicatePass.putObject("settings").putObject("effects").putArray("passOrder").add("bloom").add("bloom");
		assertInvalid(duplicatePass, "sceneData.settings.effects.passOrder[1]");
		ObjectNode duplicateMapping = template();
		duplicateMapping.putObject("settings").set("audioResponseConfig", this.mapper.readTree("""
				{"version":1,"mappings":[{"target":"size","source":"bass-hit"},{"target":"size","source":"mid-level"}]}
				"""));
		assertInvalid(duplicateMapping, "sceneData.settings.audioResponseConfig.mappings[1]");
		ObjectNode invalidMotion = template();
		invalidMotion.putObject("settings").putObject("motion").put("power_factor", 11);
		assertInvalid(invalidMotion, "sceneData.settings.motion.power_factor");
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
