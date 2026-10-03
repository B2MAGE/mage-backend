package com.bdmage.mage_backend.validation;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneSubmissionValidatorTests {

	private final ObjectMapper mapper = new ObjectMapper();
	private final SceneSubmissionValidator validator = new SceneSubmissionValidator();

	@Test
	void allBundledTemplatesAndQualityDemoScenesFitThePublishedPolicy() throws Exception {
		int checked = 0;
		for (String resource : List.of("/scene-corpus/builtin-presets.json", "/scene-corpus/demo-quality.json")) {
			try (var input = getClass().getResourceAsStream(resource)) {
				assertThat(input).as(resource).isNotNull();
				for (JsonNode row : this.mapper.readTree(input)) {
					assertThatCode(() -> this.validator.validate(row.path("sceneData")))
							.as(resource + " / " + row.path("sceneId").asText()).doesNotThrowAnyException();
					checked++;
				}
			}
		}
		assertThat(checked).isEqualTo(116);
	}

	@Test
	void acceptsSparseLegacySceneWithoutEvaluatingOrRewritingItsSource() {
		ObjectNode scene = scene("throw new Error('must never execute'); while(true) {} // untrusted source");
		JsonNode original = scene.deepCopy();
		this.validator.validate(scene);
		assertThat(scene).isEqualTo(original);
	}

	@Test
	void acceptsCurrentEditorShapeAndSelectiveAudioWithFourEffects() throws Exception {
		JsonNode scene = this.mapper.readTree("""
				{
				  "visualizer":{"shader":"let size = input(); sphere(size);", "scale":10,"skyboxPreset":6},
				  "controls":{"position0":{"x":0,"y":0,"z":5.5},"target0":{"x":0,"y":0,"z":0},"zoom0":1},
				  "intent":{"time_multiplier":1,"minimizing_factor":0.8,"power_factor":8,
				    "pointerDownMultiplier":0,"base_speed":0.2,"easing_speed":0.6,"camTilt":0,
				    "camOrientationMode":0,"camOrientationSpeed":1,"autoRotate":true,"autoRotateSpeed":0.2,"fov":75},
				  "fx":{
				    "passOrder":["glitchPass","bloom","RGBShift","dotShader","technicolorShader","luminosityShader",
				      "afterImagePass","sobelShader","colorifyShader","halftonePass","gammaCorrectionShader",
				      "kaleidoShader","copyShader","bleachBypassShader","toonShader","outputPass"],
				    "bloom":{"enabled":true,"strength":1,"radius":0.2,"threshold":0.1},
				    "toneMapping":{"method":0,"exposure":1.5},
				    "passes":{"rgbShift":true,"dot":false,"technicolor":false,"luminosity":false,
				      "afterImage":true,"sobel":false,"glitch":false,"colorify":true,"halftone":false,
				      "gammaCorrection":false,"kaleid":false,"bleachBypass":false,"toon":false,"outputPass":true},
				    "params":{"rgbShift":{"amount":0.005,"angle":0},"afterImage":{"damp":0.96},
				      "colorify":{"color":"#aB34Ff"},"kaleid":{"sides":6,"angle":0}}
				  },
				  "state":{"size":0,"pointerDown":0,"currPointerDown":0,"currAudio":0,"time":0,"volume_multiplier":0},
				  "audioResponse":"mapped-v1",
				  "audioResponseConfig":{"version":1,"sensitivity":3.74,"mappings":[
				    {"target":"size","source":"overall-hit","amount":0.1,"attack":0.04,"release":0.35}
				  ]}
				}
				""");
		assertThatCode(() -> this.validator.validate(scene)).doesNotThrowAnyException();
	}

	@Test
	void enforcesExactSourceUtf8ByteBoundaryForAsciiAndMultibyteText() {
		for (String source : List.of("a".repeat(SceneLimits.SOURCE_BYTES), "é".repeat(SceneLimits.SOURCE_BYTES / 2),
				"😀".repeat(SceneLimits.SOURCE_BYTES / 4))) {
			assertThat(source.getBytes(StandardCharsets.UTF_8)).hasSize(SceneLimits.SOURCE_BYTES);
			this.validator.validate(scene(source));
			assertInvalid(scene(source + "a"), "sceneData.visualizer.shader", "UTF-8 bytes");
		}
	}

	@Test
	void enforcesSerializedSceneLimitIncludingJsonEscapingAtExactBoundary() throws Exception {
		int overhead = this.mapper.writeValueAsBytes(scene("")).length;
		int room = SceneLimits.SCENE_BYTES - overhead;
		// Each NUL is one source byte but six serialized JSON bytes (backslash-u0000).
		String source = "\0".repeat(room / 6) + "a".repeat(room % 6);
		ObjectNode exact = scene(source);
		assertThat(this.mapper.writeValueAsBytes(exact)).hasSize(SceneLimits.SCENE_BYTES);
		this.validator.validate(exact);
		assertInvalid(scene(source + "a"), "sceneData", "Serialized scene");
	}

	@Test
	void rejectsMissingWrongTypeBlankAndInvalidUnicodeSource() {
		assertInvalid(null, "sceneData", "JSON object");
		assertInvalid(this.mapper.createArrayNode(), "sceneData", "JSON object");
		assertInvalid(this.mapper.createObjectNode(), "sceneData.visualizer", "required");
		assertInvalid(scene("\n\t "), "sceneData.visualizer.shader", "nonblank");
		ObjectNode scene = scene("sphere(1)");
		((ObjectNode) scene.get("visualizer")).put("shader", 1);
		assertInvalid(scene, "sceneData.visualizer.shader", "source text");
		assertInvalid(scene("sphere(1);\uD800"), "sceneData.visualizer.shader", "Unicode");
	}

	@Test
	void rejectsUnknownFieldsAtEveryLevelIncludingInactiveEffectSettings() throws Exception {
		for (String extra : List.of(
				"\"source\":\"run()\"", "\"schemaVersion\":1", "\"kind\":\"template\"",
				"\"renderer\":{\"width\":100000}", "\"audio\":{\"url\":\"https://example.test/music\"}",
				"\"visualizer\":{\"shader\":\"sphere(1)\",\"script\":\"run()\"}",
				"\"fx\":{\"passes\":{\"customEffect\":false}}",
				"\"fx\":{\"params\":{\"rgbShift\":{\"enabled\":false,\"url\":\"https://example.test\"}}}",
				"\"controls\":{\"position0\":{\"x\":0,\"y\":0,\"z\":0,\"code\":\"run()\"}}",
				"\"audioResponseConfig\":{\"version\":1,\"mappings\":[{\"target\":\"size\",\"source\":\"overall-hit\",\"expression\":\"run()\"}]}")) {
			ObjectNode scene = scene("sphere(1)");
			scene.setAll((ObjectNode) this.mapper.readTree("{" + extra + "}"));
			assertThatThrownBy(() -> this.validator.validate(scene)).isInstanceOf(InvalidSceneDataException.class)
					.satisfies(error -> assertThat(((InvalidSceneDataException) error).getDetails().values())
							.anyMatch(message -> message.contains("Unknown field")));
		}
	}

	@Test
	void rejectsPrototypeKeysAndDoesNotEchoUnsafeOrLongFieldNames() {
		for (String key : List.of("__proto__", "constructor", "prototype")) {
			ObjectNode scene = scene("sphere(1)");
			scene.putObject("fx").put(key, "secret");
			assertInvalid(scene, "sceneData.fx." + key, "Prototype-related");
		}
		for (String key : List.of("https://private.example/token=secret", "a".repeat(65), "é".repeat(33))) {
			ObjectNode scene = scene("sphere(1)");
			scene.put(key, "secret source");
			assertThatThrownBy(() -> this.validator.validate(scene)).isInstanceOf(InvalidSceneDataException.class)
					.satisfies(error -> {
						var details = ((InvalidSceneDataException) error).getDetails();
						assertThat(details).containsKey("sceneData");
						assertThat(details.toString()).doesNotContain(key, "secret source");
					});
		}
	}

	@Test
	void rejectsNonFiniteAndOutOfRangeNumbersWithoutCoercion() throws Exception {
		ObjectNode scene = scene("sphere(1)");
		for (double value : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
			scene.putObject("state").put("size", value);
			assertInvalid(scene, "sceneData.state.size", "finite");
		}
		for (String fragment : List.of("\"scale\":0", "\"scale\":201", "\"scale\":\"10\"",
				"\"skyboxPreset\":0", "\"skyboxPreset\":11", "\"skyboxPreset\":1.5", "\"skyboxPreset\":\"preset1\"")) {
			ObjectNode bad = scene("sphere(1)");
			((ObjectNode) bad.get("visualizer")).setAll((ObjectNode) this.mapper.readTree("{" + fragment + "}"));
			assertThatThrownBy(() -> this.validator.validate(bad)).isInstanceOf(InvalidSceneDataException.class);
		}
		assertInvalid(this.mapper.readTree("{\"visualizer\":{\"shader\":\"x\"},\"fx\":{\"bloom\":{\"enabled\":\"true\"}}}"),
				"sceneData.fx.bloom.enabled", "boolean");
	}

	@Test
	void rejectsPartialControlsBecauseTheEngineRequiresTheWholeCameraSnapshot() throws Exception {
		for (String controls : List.of("{}", "{\"zoom0\":1}",
				"{\"position0\":{\"x\":0,\"y\":0,\"z\":5},\"target0\":{\"x\":0,\"y\":0,\"z\":0}}")) {
			ObjectNode scene = scene("sphere(1)");
			scene.set("controls", this.mapper.readTree(controls));
			assertThatThrownBy(() -> this.validator.validate(scene)).isInstanceOf(InvalidSceneDataException.class)
					.satisfies(error -> assertThat(((InvalidSceneDataException) error).getDetails().values())
							.anyMatch(message -> message.contains("required")));
		}
	}

	@Test
	void rejectsUnsupportedPassesDuplicatesAndFifthOptionalEffectEvenWhenOutputIsEnabled() throws Exception {
		ObjectNode scene = scene("sphere(1)");
		scene.set("fx", this.mapper.readTree("{\"passOrder\":[\"bloom\",\"bloom\"]}"));
		assertInvalid(scene, "sceneData.fx.passOrder[1]", "Duplicate");
		scene.set("fx", this.mapper.readTree("{\"passOrder\":[\"customPass\"]}"));
		assertInvalid(scene, "sceneData.fx.passOrder[0]", "Unsupported");
		scene.set("fx", this.mapper.readTree("{\"bloom\":{\"enabled\":true},\"passes\":{\"rgbShift\":true,\"dot\":true,\"colorify\":true,\"outputPass\":true}}"));
		this.validator.validate(scene);
		((ObjectNode) scene.path("fx").path("passes")).put("toon", true);
		assertInvalid(scene, "sceneData.fx", "at most 4");
	}

	@Test
	void rejectsInvalidAudioVersionDuplicateTargetsAndBounds() throws Exception {
		for (String config : List.of("{\"version\":2}", "{\"version\":1,\"sensitivity\":4.01}",
				"{\"version\":1,\"mappings\":[{\"target\":\"size\",\"source\":\"overall-hit\",\"amount\":4.01}]}",
				"{\"version\":1,\"mappings\":[{\"target\":\"size\",\"source\":\"overall-hit\",\"attack\":2.01}]}",
				"{\"version\":1,\"mappings\":[{\"target\":\"size\",\"source\":\"overall-hit\",\"release\":5.01}]}",
				"{\"version\":1,\"mappings\":[{\"target\":\"size\",\"source\":\"overall-hit\"},{\"target\":\"size\",\"source\":\"bass-hit\"}]}")) {
			ObjectNode scene = scene("sphere(1)");
			scene.set("audioResponseConfig", this.mapper.readTree(config));
			assertThatThrownBy(() -> this.validator.validate(scene)).isInstanceOf(InvalidSceneDataException.class);
		}
	}

	@Test
	void validatesInactiveEffectParametersColorsAndToneMappingEnums() throws Exception {
		for (String fx : List.of(
				"{\"bloom\":{\"enabled\":false,\"strength\":10.01}}",
				"{\"bloom\":{\"enabled\":false,\"radius\":10.01}}",
				"{\"params\":{\"afterImage\":{\"damp\":1.01}}}",
				"{\"params\":{\"rgbShift\":{\"amount\":0.101}}}",
				"{\"params\":{\"kaleid\":{\"sides\":25}}}",
				"{\"params\":{\"colorify\":{\"color\":\"red\"}}}",
				"{\"params\":{\"colorify\":{\"color\":16777215}}}",
				"{\"toneMapping\":{\"method\":5}}",
				"{\"toneMapping\":{\"method\":\"0\"}}",
				"{\"toneMapping\":{\"exposure\":10.01}}")) {
			ObjectNode scene = scene("sphere(1)");
			scene.set("fx", this.mapper.readTree(fx));
			assertThatThrownBy(() -> this.validator.validate(scene)).isInstanceOf(InvalidSceneDataException.class);
		}
	}

	@Test
	void stopsExcessiveDepthArrayLengthObjectKeysAndTotalKeysBeforeSchemaTraversal() {
		ObjectNode deep = scene("sphere(1)");
		ObjectNode cursor = deep;
		for (int depth = 1; depth <= SceneLimits.SCENE_DEPTH; depth++) {
			cursor = cursor.putObject("child");
		}
		assertThatThrownBy(() -> this.validator.validate(deep)).isInstanceOf(InvalidSceneDataException.class)
				.satisfies(error -> assertThat(((InvalidSceneDataException) error).getDetails().values())
						.anyMatch(message -> message.contains("nesting")));
		ObjectNode arrayScene = scene("sphere(1)");
		ArrayNode array = arrayScene.putArray("items");
		for (int count = 0; count <= SceneLimits.ARRAY_ITEMS; count++) {
			array.add(0);
		}
		assertInvalid(arrayScene, "sceneData.items", "array items");
		ObjectNode wide = scene("sphere(1)");
		for (int count = 0; count < SceneLimits.OBJECT_KEYS; count++) {
			wide.put("key" + count, 0);
		}
		assertInvalid(wide, "sceneData", "object fields");
		ObjectNode manyKeys = scene("sphere(1)");
		ArrayNode branches = manyKeys.putArray("branches");
		for (int branch = 0; branch < 9; branch++) {
			ObjectNode item = branches.addObject();
			for (int count = 0; count < 60; count++) {
				item.put("field" + count, count);
			}
		}
		assertInvalid(manyKeys, "sceneData", "total object fields");
		ObjectNode manyValues = scene("sphere(1)");
		ArrayNode outer = manyValues.putArray("items");
		for (int branch = 0; branch < 64; branch++) {
			ArrayNode inner = outer.addArray();
			for (int count = 0; count < 64; count++) {
				inner.add(0);
			}
		}
		assertInvalid(manyValues, "sceneData", "JSON values");
	}

	@Test
	void policyAccessCannotChangeAuthoritativeLimitsOrSchema() {
		ObjectNode copy = (ObjectNode) SceneLimits.policy();
		((ObjectNode) copy.get("limits")).put("sourceBytes", 1);
		((ObjectNode) copy.path("scene").path("fields")).remove("visualizer");
		this.validator.validate(scene("sphere(1)"));
		assertThat(SceneLimits.policy().path("limits").path("sourceBytes").intValue()).isEqualTo(SceneLimits.SOURCE_BYTES);
		assertThat(SceneLimits.policy().path("runtimeCeilings").path("raymarchIterations").intValue()).isEqualTo(200);
	}

	private ObjectNode scene(String source) {
		ObjectNode scene = this.mapper.createObjectNode();
		scene.putObject("visualizer").put("shader", source);
		return scene;
	}

	private void assertInvalid(JsonNode scene, String path, String message) {
		assertThatThrownBy(() -> this.validator.validate(scene)).isInstanceOf(InvalidSceneDataException.class)
				.satisfies(error -> assertThat(((InvalidSceneDataException) error).getDetails())
						.containsKey(path)
						.satisfies(details -> assertThat(details.get(path)).contains(message)));
	}
}
