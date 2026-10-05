package com.bdmage.mage_backend.validation;

import java.util.List;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuilderSceneDocumentValidatorTests {
	private final ObjectMapper json = new ObjectMapper();
	private final SceneDocumentValidator validator = new SceneDocumentValidator();

	@Test
	void normalizesAllAllowedOperationsAndSixteenMinimalObjectsWithinExistingBudgets() {
		ObjectNode document = builder();
		for (int index = 0; index < 16; index++) {
			document.withArray("objects").addObject().put("id", "shape-" + index)
					.putObject("operation").put("type", List.of("sphere", "box", "torus", "cylinder").get(index % 4));
		}
		JsonNode original = document.deepCopy();
		JsonNode normalized = this.validator.validateAndNormalize(document);
		assertThat(normalized.path("objects")).hasSize(16);
		for (int index = 0; index < 16; index++) {
			JsonNode object = normalized.path("objects").get(index);
			assertThat(object.path("id").asText()).isEqualTo("shape-" + index);
			assertThat(object.path("transform").path("position").path("x").asDouble()).isZero();
			assertThat(object.path("transform").path("scale").path("x").asDouble()).isEqualTo(1);
			assertThat(object.path("material").path("color").asText()).isEqualTo("#8066ff");
			assertThat(object.path("bindings")).isEmpty();
		}
		assertThat(normalized).isEqualTo(this.validator.validateAndNormalize(normalized));
		assertThat(document).isEqualTo(original);
		assertThat(normalized.findValues("shader")).isEmpty();
		document.withArray("objects").addObject().put("id", "shape-16").putObject("operation").put("type", "sphere");
		assertInvalid(document, "sceneData.objects");
	}

	@Test
	void preservesExplicitTransformsMaterialsBindingsAndSceneControls() throws Exception {
		ObjectNode document = (ObjectNode) this.json.readTree("""
				{"schemaVersion":1,"kind":"builder","builderVersion":1,"objects":[
				 {"id":"persistent-id","name":"Café shape","operation":{"type":"box","width":2,"height":3,"depth":4},
				  "transform":{"position":{"x":1,"y":-2,"z":3},"rotation":{"x":0.2,"y":0.4,"z":-0.3},"scale":{"x":1,"y":2,"z":0.5}},
				  "material":{"color":"#ABCDEF","metalness":0.4,"shininess":0.6},
				  "bindings":[{"target":"scale.x","source":"bass-hit","mode":"add","amount":0.6,"offset":0.1,"attack":0.07,"release":0.73},
				    {"target":"material.metalness","source":"pointer-x","mode":"replace","amount":-1,"offset":0.5,"attack":0,"release":0}]}],
				 "parameters":{"scale":12,"speed":0.7},"settings":{"camera":{"fov":90},"bloom":{"enabled":true,"strength":0.4}}}
				""");
		JsonNode original = document.deepCopy();
		JsonNode normalized = this.validator.validateAndNormalize(document);
		assertThat(normalized.path("objects")).isEqualTo(document.path("objects"));
		assertThat(normalized.path("parameters")).isEqualTo(document.path("parameters"));
		assertThat(normalized.path("settings").path("camera").path("fov").intValue()).isEqualTo(90);
		assertThat(normalized.path("settings").path("bloom").path("strength").asDouble()).isEqualTo(0.4);
		assertThat(document).isEqualTo(original);
	}

	@Test
	void rejectsDuplicateIdentitiesAndBindingsAndExecutableOrUnsupportedFields() throws Exception {
		for (String field : List.of("shader", "source", "expression", "url", "imports", "trusted", "sceneMode")) {
			ObjectNode document = builder().put(field, "doNotExecute()");
			assertInvalid(document, "sceneData." + field);
		}
		ObjectNode duplicate = builder();
		duplicate.withArray("objects").addObject().put("id", "same").putObject("operation").put("type", "sphere");
		duplicate.withArray("objects").addObject().put("id", "same").putObject("operation").put("type", "box");
		assertInvalid(duplicate, "sceneData.objects[1]");
		ObjectNode document = builderWithObject();
		ObjectNode object = (ObjectNode) document.path("objects").get(0);
		object.putArray("bindings").addObject().put("target", "scale.x").put("source", "bass-hit");
		object.withArray("bindings").addObject().put("target", "scale.x").put("source", "pointer-x");
		assertInvalid(document, "sceneData.objects[0].bindings[1]");
		object.remove("bindings");
		((ObjectNode) object.path("operation")).put("radius", "1 + input('bass')");
		assertInvalid(document, "sceneData.objects[0].operation.radius");
		((ObjectNode) object.path("operation")).put("radius", 1);
		object.putObject("transform").putObject("position").put("x", Double.POSITIVE_INFINITY);
		assertInvalid(document, "sceneData.objects[0].transform.position.x");
	}

	@Test
	void countsMaterializedDefaultsAgainstTheUnchangedWholeDocumentBudget() {
		ObjectNode document = builder();
		for (int index = 0; index < 16; index++) {
			ObjectNode object = document.withArray("objects").addObject().put("id", "shape-" + index);
			object.putObject("operation").put("type", "sphere");
			for (String target : List.of("scale.x", "scale.y", "position.x", "position.y")) {
				object.withArray("bindings").addObject().put("target", target).put("source", "bass-level");
			}
		}
		new SceneSubmissionValidator().validateTransportBudget(document);
		assertThat(this.validator.validateContract(document).path("objects")).hasSize(16);
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document))
				.isInstanceOfSatisfying(InvalidSceneDataException.class, error ->
						assertThat(error.getDetails()).containsEntry("sceneData", "Too many total object fields; maximum is 512."));
	}

	@Test
	void enforcesSceneEffectBudgetAndVersionsWithoutConvertingBuilderToCustom() {
		ObjectNode document = builder().put("builderVersion", 2);
		assertInvalid(document, "sceneData.builderVersion");
		document.put("builderVersion", 1);
		ObjectNode settings = document.putObject("settings");
		settings.putObject("bloom").put("enabled", true);
		settings.putObject("tint").put("enabled", true);
		settings.putObject("effects").putObject("passes").put("rgbShift", true).put("afterImage", true).put("glitch", true);
		assertInvalid(document, "sceneData.settings.effects");
	}

	private ObjectNode builder() {
		ObjectNode document = this.json.createObjectNode().put("schemaVersion", 1).put("kind", "builder").put("builderVersion", 1);
		document.putArray("objects");
		return document;
	}
	private ObjectNode builderWithObject() {
		ObjectNode document = builder();
		document.withArray("objects").addObject().put("id", "shape").putObject("operation").put("type", "sphere");
		return document;
	}
	private void assertInvalid(JsonNode document, String path) {
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document))
				.isInstanceOfSatisfying(InvalidSceneDataException.class, error -> assertThat(error.getDetails()).containsKey(path));
	}
}
