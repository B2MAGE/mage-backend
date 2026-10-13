package com.bdmage.mage_backend.validation;

import java.util.List;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuilderDriverValidatorTests {

	private final ObjectMapper json = new ObjectMapper();
	private final SceneDocumentValidator validator = new SceneDocumentValidator();

	@Test
	void preservesOrderedInfluencesAndWholeObjectOrSpatialInputsAsPlainTypedData() throws Exception {
		ObjectNode document = builder();
		JsonNode original;
		driver(document, "rotation.y", this.json.readTree("""
				{"type":"multiply","left":{"type":"input","source":"time"},"right":{"type":"constant","value":0.5}}
				"""));
		driver(document, "rotation.y", input("bass-hit")).put("mode", "add");
		driver(document, "position.x", this.json.readTree("""
				{"type":"component","axis":"x","input":{"type":"sin","input":{"type":"multiply",
				 "left":{"type":"input","source":"local-position"},"right":{"type":"constant","value":{"x":1,"y":2,"z":3}}}}}
				"""));
		original = document.deepCopy();
		JsonNode normalized = this.validator.validateAndNormalize(document);
		assertThat(normalized.path("objects").get(0).path("drivers")).hasSize(3);
		assertThat(normalized.path("objects").get(0).path("drivers").get(0).path("mode").asText()).isEqualTo("add");
		assertThat(this.validator.validateAndNormalize(this.json.readTree(normalized.toString()))).isEqualTo(normalized);
		assertThat(document).isEqualTo(original);
	}

	@Test
	void acceptsAllApprovedInputsAndRequiresVectorComponentsForScalarTargets() {
		for (String source : List.of("time", "bass-level", "bass-hit", "mid-level", "mid-hit", "treble-level", "treble-hit",
				"overall-level", "overall-hit", "pointer-x", "pointer-y", "pointer-down", "scene-size", "scene-pointer-down")) {
			ObjectNode document = builder();
			driver(document, "position.x", input(source));
			assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(1);
		}
		for (String source : List.of("local-position", "group-position", "world-position", "view-ray", "scene-pointer")) {
			ObjectNode document = builder();
			driver(document, "position.x", input(source));
			assertInvalid(document, "sceneData.objects[0].drivers[0].expression");
			ObjectNode component = this.json.createObjectNode().put("type", "component").put("axis", "x");
			component.set("input", input(source));
			((ObjectNode) document.path("objects").get(0).path("drivers").get(0)).set("expression", component);
			assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(1);
		}
	}

	@Test
	void rejectsIncompatibleTypesUnknownInputsAndInvalidRanges() throws Exception {
		for (String expression : List.of(
				"{\"type\":\"input\",\"source\":\"camera-position\"}",
				"{\"type\":\"constant\",\"value\":1001}",
				"{\"type\":\"add\",\"left\":{\"type\":\"input\",\"source\":\"time\"},\"right\":{\"type\":\"input\",\"source\":\"view-ray\"}}",
				"{\"type\":\"component\",\"axis\":\"x\",\"input\":{\"type\":\"input\",\"source\":\"time\"}}",
				"{\"type\":\"clamp\",\"min\":2,\"max\":1,\"input\":{\"type\":\"input\",\"source\":\"time\"}}",
				"{\"type\":\"constant\",\"value\":1,\"source\":\"NEVER_EXECUTE()\"}")) {
			ObjectNode document = builder();
			driver(document, "position.x", this.json.readTree(expression));
			assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOf(InvalidSceneDataException.class);
		}
		for (double number : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
			ObjectNode document = builder();
			driver(document, "position.x", constant(number));
			assertInvalid(document, "sceneData.objects[0].drivers[0].expression.value");
		}
	}

	@Test
	void limitsExpressionDepthAndNodesIndependentlyOfTheWholeTransportBudget() {
		ObjectNode document = builder();
		JsonNode nested = constant(1);
		for (int index = 0; index < 12; index++) {
			ObjectNode next = this.json.createObjectNode().put("type", "sin");
			next.set("input", nested);
			nested = next;
		}
		driver(document, "position.x", nested);
		assertThatThrownBy(() -> this.validator.validateContract(document)).isInstanceOfSatisfying(InvalidSceneDataException.class, error ->
				assertThat(error.getDetails().values()).anyMatch(message -> message.contains("depth limit")));
		((ObjectNode) document.path("objects").get(0).path("drivers").get(0)).set("expression", tree(5));
		assertThatThrownBy(() -> this.validator.validateContract(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails().values()).anyMatch(message -> message.contains("node limit")));
	}

	@Test
	void enforcesPerOwnerAndWholeSceneDriverCounts() {
		ObjectNode document = builder();
		for (int index = 0; index < 13; index++) driver(document, "position.x", input("time"));
		assertInvalid(document, "sceneData.objects[0].drivers");
		((com.fasterxml.jackson.databind.node.ArrayNode) document.path("objects").get(0).path("drivers")).remove(12);
		for (int index = 1; index < 5; index++) {
			ObjectNode copy = document.path("objects").get(0).deepCopy();
			copy.put("id", "shape-" + index);
			document.withArray("objects").add(copy);
		}
		ObjectNode finalObject = document.path("objects").get(0).deepCopy();
		finalObject.put("id", "shape-5");
		((com.fasterxml.jackson.databind.node.ArrayNode) finalObject.path("drivers")).removeAll();
		for (int index = 0; index < 4; index++) finalObject.withArray("drivers").add(document.path("objects").get(0).path("drivers").get(0));
		document.withArray("objects").add(finalObject);
		assertThat(this.validator.validateContract(document).path("objects")).hasSize(6);
		finalObject.withArray("drivers").add(document.path("objects").get(0).path("drivers").get(0));
		assertThatThrownBy(() -> this.validator.validateContract(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails()).containsEntry("sceneData.objects", "authoredDrivers requires 65; maximum is 64."));
	}

	@Test
	void includesArrangementExpansionInTheDriverWorkload() {
		ObjectNode document = builder();
		for (int index = 0; index < 4; index++) driver(document, "position.x", tree(4)); // 31 nodes x 4 drivers x 8 copies.
		((ObjectNode) document.path("objects").get(0)).withArray("arrangements").addObject()
				.put("type", "linear").put("axis", "x").put("count", 8).put("spacing", 1);
		assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(1);
		driver(document, "position.x", tree(4));
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails()).containsEntry("sceneData.objects", "driverOperations requires 1240; maximum is 1024."));
	}

	@Test
	void limitsRequiredSignalsAndBindingsWithoutChargingInlinedConstants() {
		ObjectNode document = withBindingSlots(64);
		for (int index = 0; index < 8; index++) driver(document, "position.x", uniqueTree(4, index * 16)); // 128 inlined constants.
		assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(16);
		driver(document, "position.y", input("bass-hit"));
		assertThatThrownBy(() -> this.validator.validateContract(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails()).containsEntry("sceneData.objects", "liveUniforms requires 65; maximum is 64."));
		ObjectNode signals = withBindingSlots(63);
		for (int index = 0; index < 8; index++) driver(signals, "position.x", input("bass-hit"));
		assertThat(this.validator.validateAndNormalize(signals).path("objects").get(0).path("drivers")).hasSize(8);
	}

	@Test
	void inlinesScalarVectorAndClampConstantsButKeepsExpressionWorkAndLiveSignals() throws Exception {
		ObjectNode document = builder();
		driver(document, "position.x", constant(1));
		driver(document, "position.y", this.json.readTree("""
				{"type":"component","axis":"x","input":{"type":"constant","value":{"x":1,"y":0,"z":-0.0}}}
				"""));
		driver(document, "position.z", this.json.readTree("""
				{"type":"clamp","min":0,"max":1,"input":{"type":"input","source":"scene-size"}}
				"""));
		ObjectNode copy = document.path("objects").get(0).deepCopy();
		copy.put("id", "copy");
		document.withArray("objects").add(copy);
		ObjectNode composition = document.putObject("composition").put("version", 1);
		composition.putArray("roots").add("group");
		ObjectNode group = composition.putArray("groups").addObject().put("id", "group");
		group.putArray("children").add("shape").add("copy");
		group.putArray("drivers").addObject().put("target", "position.x").put("mode", "add").set("expression", constant(1));
		JsonNode normalized = this.validator.validateAndNormalize(document);
		JsonNode policy;
		try (var input = getClass().getResourceAsStream("/contracts/scenes/builder-rendering.v1.json")) {
			assertThat(input).isNotNull();
			policy = this.json.readTree(input);
		}
		BuilderDriverValidator.Workload work = BuilderDriverValidator.validate(normalized, policy.path("driverLimits"), policy.path("operationCosts"));
		assertThat(work.liveUniforms()).isEqualTo(1); // Shared scene-size input; constants are inlined.
		assertThat(work.drivers()).isEqualTo(7);
		assertThat(work.driverOperations()).isEqualTo(11); // Inlining changes storage, not expression cost.
	}

	@Test
	void nativeScalarInputsConsumeShaderSlotsButReuseTheScenePointerVector() {
		ObjectNode document = withBindingSlots(63);
		driver(document, "operation.radius", input("scene-size"));
		ObjectNode component = this.json.createObjectNode().put("type", "component").put("axis", "x");
		component.set("input", input("scene-pointer"));
		driver(document, "rotation.x", component);
		JsonNode normalized = this.validator.validateAndNormalize(document);
		assertThat(normalized.path("objects").get(0).path("drivers")).hasSize(2);
		assertThat(this.validator.validateAndNormalize(normalized)).isEqualTo(normalized);
		driver(document, "rotation.y", input("scene-pointer-down"));
		assertThatThrownBy(() -> this.validator.validateContract(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails()).containsEntry("sceneData.objects", "liveUniforms requires 65; maximum is 64."));
	}

	@Test
	void shapeDimensionsAndGroupTargetsMustExistOnTheirOwner() throws Exception {
		ObjectNode document = builder();
		driver(document, "operation.height", constant(1));
		assertInvalid(document, "sceneData.objects[0].drivers[0].target");
		((ObjectNode) document.path("objects").get(0).path("operation")).put("type", "cylinder");
		assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(1);
		document.set("composition", this.json.readTree("""
				{"version":1,"roots":["pair"],"groups":[{"id":"pair","children":["shape"],
				 "drivers":[{"target":"blend","mode":"add","expression":{"type":"constant","value":0.2}}]}]}
				"""));
		assertInvalid(document, "sceneData.composition.groups[0].drivers[0].target");
		ObjectNode group = (ObjectNode) document.path("composition").path("groups").get(0);
		group.putObject("operation").put("type", "smooth-union");
		assertThat(this.validator.validateAndNormalize(document).path("composition").path("groups")).hasSize(1);
		((ObjectNode) group.path("drivers").get(0)).put("target", "material.metalness");
		assertInvalid(document, "sceneData.composition.groups[0].drivers[0].target");
		group.putObject("material").put("metalness", 0.5);
		assertThat(this.validator.validateAndNormalize(document).path("composition").path("groups")).hasSize(1);
		((ObjectNode) group.path("drivers").get(0)).put("target", "scale");
		assertThat(this.validator.validateAndNormalize(document).path("composition").path("groups")).hasSize(1);
	}

	private ObjectNode builder() {
		ObjectNode document = this.json.createObjectNode().put("schemaVersion", 1).put("kind", "builder").put("builderVersion", 1);
		document.putArray("objects").addObject().put("id", "shape").putObject("operation").put("type", "sphere");
		return document;
	}
	private ObjectNode withBindingSlots(int count) {
		ObjectNode document = builder();
		ObjectNode prototype = document.path("objects").get(0).deepCopy();
		for (int index = 1; index < 16; index++) document.withArray("objects").add(prototype.deepCopy().put("id", "shape-" + index));
		String[] targets = { "scale.x", "scale.y", "scale.z", "position.x" };
		for (int index = 0; index < count; index++) {
			((ObjectNode) document.path("objects").get(index / 4)).withArray("bindings").addObject()
					.put("target", targets[index % 4]).put("source", "bass-hit");
		}
		return document;
	}
	private ObjectNode driver(ObjectNode document, String target, JsonNode expression) {
		ObjectNode driver = ((ObjectNode) document.path("objects").get(0)).withArray("drivers").addObject().put("target", target).put("mode", "add");
		driver.set("expression", expression);
		return driver;
	}
	private ObjectNode input(String source) { return this.json.createObjectNode().put("type", "input").put("source", source); }
	private ObjectNode constant(double value) { return this.json.createObjectNode().put("type", "constant").put("value", value); }
	private JsonNode tree(int branches) {
		if (branches == 0) return constant(0.01);
		ObjectNode result = this.json.createObjectNode().put("type", "add");
		result.set("left", tree(branches - 1));
		result.set("right", tree(branches - 1));
		return result;
	}
	private JsonNode uniqueTree(int branches, int first) {
		if (branches == 0) return constant((first + 1) / 1000d);
		ObjectNode result = this.json.createObjectNode().put("type", "add");
		result.set("left", uniqueTree(branches - 1, first));
		result.set("right", uniqueTree(branches - 1, first + (1 << (branches - 1))));
		return result;
	}
	private void assertInvalid(JsonNode document, String path) {
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document)).isInstanceOfSatisfying(InvalidSceneDataException.class,
				error -> assertThat(error.getDetails()).containsKey(path));
	}
}
