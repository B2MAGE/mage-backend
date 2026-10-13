package com.bdmage.mage_backend.validation;

import java.util.List;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuilderDeformationValidatorTests {

	private final ObjectMapper json = new ObjectMapper();
	private final SceneDocumentValidator validator = new SceneDocumentValidator();

	@Test
	void preservesPreciseSignedRgbAndScopedNoiseWithVectorFrequencyOffsetAndDrift() throws Exception {
		ObjectNode document = builder();
		ObjectNode object = object(document);
		object.putObject("material").set("color", this.json.readTree("{\"x\":0.08256470259178794,\"y\":-0.25,\"z\":0.763003005740327}"));
		object.putArray("modifiers").addObject().put("type", "expand").put("amount", 0);
		ObjectNode expression = (ObjectNode) this.json.readTree("""
			{"type":"noise","input":{"type":"add",
			 "left":{"type":"multiply","left":{"type":"input","source":"group-position","ancestor":6},
			         "right":{"type":"constant","value":0.1080185521425232}},
			 "right":{"type":"add","left":{"type":"constant","value":{"x":1,"y":2,"z":3}},
			          "right":{"type":"multiply","left":{"type":"input","source":"time"},
			                   "right":{"type":"constant","value":{"x":0.1,"y":0,"z":-0.2}}}}}}
			""");
		driver(object, "modifier.expand.amount", expression);
		driver(object, "material.color.r", component("view-ray", "x"));
		JsonNode original = document.deepCopy();
		JsonNode normalized = this.validator.validateAndNormalize(document);
		assertThat(normalized.path("objects").get(0).path("material").path("color")).isEqualTo(object.path("material").path("color"));
		assertThat(normalized.path("objects").get(0).path("drivers")).isEqualTo(object.path("drivers"));
		assertThat(this.validator.validateAndNormalize(this.json.readTree(normalized.toString()))).isEqualTo(normalized);
		assertThat(document).isEqualTo(original);
	}

	@Test
	void onlyNoiseWithVectorInputAndGroupPositionWithBoundedAncestorAreValid() throws Exception {
		for (String expression : List.of(
				"{\"type\":\"noise\",\"input\":{\"type\":\"input\",\"source\":\"time\"}}",
				"{\"type\":\"noise\",\"input\":{\"type\":\"noise\",\"input\":{\"type\":\"input\",\"source\":\"local-position\"}}}",
				"{\"type\":\"noise\",\"input\":{\"type\":\"input\",\"source\":\"world-position\"},\"source\":\"NEVER_EXECUTE()\"}",
				"{\"type\":\"input\",\"source\":\"time\",\"ancestor\":1}",
				"{\"type\":\"component\",\"axis\":\"x\",\"input\":{\"type\":\"input\",\"source\":\"world-position\",\"ancestor\":1}}",
				"{\"type\":\"noise\",\"input\":{\"type\":\"input\",\"source\":\"group-position\",\"ancestor\":0}}",
				"{\"type\":\"noise\",\"input\":{\"type\":\"input\",\"source\":\"group-position\",\"ancestor\":7}}",
				"{\"type\":\"noise\",\"input\":{\"type\":\"input\",\"source\":\"group-position\",\"ancestor\":1.5}}")) {
			ObjectNode document = builder();
			driver(object(document), "position.x", this.json.readTree(expression));
			assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOf(InvalidSceneDataException.class);
		}
		for (int ancestor = 1; ancestor <= 6; ancestor++) {
			ObjectNode document = builder();
			driver(object(document), "position.x", noise(input("group-position").put("ancestor", ancestor)));
			// With no parent, all ancestor selections deliberately resolve to world coordinates.
			assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(1);
		}
	}

	@Test
	void rgbChannelsRejectUnknownMissingOutOfRangeAndNonFiniteValues() throws Exception {
		for (String color : List.of("{\"x\":0.1,\"y\":0.2}", "{\"x\":0.1,\"y\":0.2,\"z\":1.01}",
				"{\"x\":-1.01,\"y\":0.2,\"z\":0.3}", "{\"x\":0.1,\"y\":0.2,\"z\":0.3,\"source\":\"code\"}")) {
			ObjectNode document = builder();
			object(document).putObject("material").set("color", this.json.readTree(color));
			assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOf(InvalidSceneDataException.class);
		}
		for (double number : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
			ObjectNode document = builder();
			object(document).putObject("material").putObject("color").put("x", number).put("y", 0).put("z", 0);
			assertInvalid(document, "sceneData.objects[0].material.color.x");
		}
	}

	@Test
	void modifierAndGroupColorTargetsRequireTheirActualOwnerFields() {
		for (boolean groupOwner : List.of(false, true)) {
			for (String type : List.of("expand", "shell")) {
				ObjectNode document = builder();
				ObjectNode owner = groupOwner ? group(document) : object(document);
				String target = type.equals("expand") ? "modifier.expand.amount" : "modifier.shell.thickness";
				driver(owner, target, noise(input("local-position")));
				String path = groupOwner ? "sceneData.composition.groups[0]" : "sceneData.objects[0]";
				assertInvalid(document, path + ".drivers[0].target");
				owner.putArray("modifiers").addObject().put("type", type);
				assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(1);
			}
		}
		ObjectNode document = builder();
		ObjectNode group = group(document);
		driver(group, "material.color.g", component("view-ray", "y"));
		assertInvalid(document, "sceneData.composition.groups[0].drivers[0].target");
		group.putObject("material");
		assertThat(this.validator.validateAndNormalize(document).path("composition").path("groups")).hasSize(1);
		group.putArray("modifiers").addObject().put("type", "twist");
		assertInvalid(document, "sceneData.composition.groups[0].modifiers[0].type");
		group.putArray("modifiers").addObject().put("type", "expand");
		group.withArray("modifiers").addObject().put("type", "expand");
		assertInvalid(document, "sceneData.composition.groups[0].modifiers[1]");
	}

	@Test
	void weightsNestedNoiseAndEveryArrangedObjectCopyWithoutMultiplyingGroupWork() {
		ObjectNode document = builder();
		ObjectNode object = object(document);
		object.putArray("arrangements").addObject().put("type", "linear").put("axis", "x").put("count", 8).put("spacing", 1);
		for (int index = 0; index < 2; index++) driver(object, "position.x", noise(input("local-position")));
		assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(1); // 2 * (8 + 1) * 8 = 144
		driver(object, "position.x", noise(input("local-position")));
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails()).containsEntry("sceneData.objects", "driverOperations requires 216; maximum is 192."));
		object.remove("drivers");
		ObjectNode group = group(document);
		for (int index = 0; index < 3; index++) driver(group, "position.x", noise(input("local-position")));
		assertThat(this.validator.validateAndNormalize(document).path("composition").path("groups")).hasSize(1); // 27, not 216
	}

	@Test
	void chargesPostCompositionGroupModifiersAgainstTheSameWholeSceneBudget() {
		ObjectNode document = builder();
		ObjectNode object = object(document);
		object.putArray("arrangements").addObject().put("type", "linear").put("axis", "x").put("count", 2).put("spacing", 1);
		object.withArray("arrangements").addObject().put("type", "radial").put("axis", "y").put("count", 8).put("radius", 1);
		for (String type : List.of("expand", "shell", "twist")) object.withArray("modifiers").addObject().put("type", type);
		ObjectNode group = group(document);
		assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(1); // 16 * (1 + 2 + 2) = 80
		group.putArray("modifiers").addObject().put("type", "expand");
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails()).containsEntry("sceneData.objects", "modifierOperations requires 81; maximum is 80."));
	}

	@Test
	void allowsSixOrderedCompositionScopesAndRejectsTheSeventh() {
		ObjectNode document = builder();
		ObjectNode composition = document.putObject("composition").put("version", 1);
		String child = "shape";
		for (int index = 0; index < 6; index++) {
			ObjectNode group = composition.withArray("groups").addObject().put("id", "group-" + index);
			group.putArray("children").add(child);
			child = "group-" + index;
		}
		composition.putArray("roots").add(child);
		assertThat(this.validator.validateAndNormalize(document).path("composition").path("groups")).hasSize(6);
		composition.withArray("groups").addObject().put("id", "group-6").putArray("children").add(child);
		composition.putArray("roots").add("group-6");
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails().values()).contains("Groups may be nested at most 6 levels deep."));
	}

	private ObjectNode builder() {
		ObjectNode document = this.json.createObjectNode().put("schemaVersion", 1).put("kind", "builder").put("builderVersion", 1);
		document.putArray("objects").addObject().put("id", "shape").putObject("operation").put("type", "sphere");
		return document;
	}
	private ObjectNode object(ObjectNode document) { return (ObjectNode) document.path("objects").get(0); }
	private ObjectNode group(ObjectNode document) {
		ObjectNode composition = document.putObject("composition").put("version", 1);
		composition.putArray("roots").add("group");
		ObjectNode group = composition.putArray("groups").addObject().put("id", "group");
		group.putArray("children").add("shape");
		return group;
	}
	private void driver(ObjectNode owner, String target, JsonNode expression) {
		owner.withArray("drivers").addObject().put("target", target).put("mode", "replace").set("expression", expression);
	}
	private ObjectNode input(String source) { return this.json.createObjectNode().put("type", "input").put("source", source); }
	private ObjectNode noise(JsonNode input) { return this.json.createObjectNode().put("type", "noise").set("input", input); }
	private ObjectNode component(String source, String axis) {
		return this.json.createObjectNode().put("type", "component").put("axis", axis).set("input", input(source));
	}
	private void assertInvalid(JsonNode document, String path) {
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails()).containsKey(path));
	}
}
