package com.bdmage.mage_backend.validation;

import java.util.List;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuilderFrameLatticeValidatorTests {

	private final ObjectMapper json = new ObjectMapper();
	private final SceneDocumentValidator validator = new SceneDocumentValidator();

	@Test
	void normalizesFramesAndLatticesWithoutLosingFractionalDensityOrAuthoredInput() {
		ObjectNode document = builder("box-frame");
		ObjectNode lattice = document.withArray("objects").addObject().put("id", "grid");
		lattice.putObject("operation").put("type", "lattice").put("iterations", 1.99);
		JsonNode original = document.deepCopy();
		JsonNode normalized = this.validator.validateAndNormalize(document);
		assertThat(normalized.path("objects").get(0).path("operation"))
				.isEqualTo(this.json.createObjectNode().put("type", "box-frame").put("width", 1).put("height", 1)
						.put("depth", 1).put("thickness", 0.08));
		assertThat(normalized.path("objects").get(1).path("operation"))
				.isEqualTo(this.json.createObjectNode().put("type", "lattice").put("iterations", 1.99)
						.put("spacing", 0.3).put("thickness", 0.02));
		assertThat(this.validator.validateAndNormalize(normalized)).isEqualTo(normalized);
		assertThat(document).isEqualTo(original);
	}

	@Test
	void rejectsUnsupportedGeometryFieldsAndNonFiniteOrOutOfRangeParameters() {
		for (String shape : List.of("box-frame", "lattice")) {
			for (String field : List.of("source", "shader", "callback", "cells")) {
				ObjectNode document = builder(shape);
				operation(document).put(field, "NEVER_EXECUTE()");
				assertInvalid(document, "sceneData.objects[0].operation." + field);
			}
			for (String field : shape.equals("box-frame") ? List.of("width", "height", "depth", "thickness")
					: List.of("iterations", "spacing", "thickness")) {
				for (double value : new double[] { 0, -1, 11, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
					ObjectNode document = builder(shape);
					operation(document).put(field, value);
					assertInvalid(document, "sceneData.objects[0].operation." + field);
				}
			}
		}
		ObjectNode density = builder("lattice");
		operation(density).put("iterations", 5.001);
		assertInvalid(density, "sceneData.objects[0].operation.iterations");
	}

	@Test
	void supportsDimensionAndThicknessDriversButKeepsDensityStructural() {
		for (String shape : List.of("box-frame", "lattice")) {
			ObjectNode document = builder(shape);
			ObjectNode object = (ObjectNode) document.path("objects").get(0);
			for (String target : shape.equals("box-frame")
					? List.of("operation.width", "operation.height", "operation.depth", "operation.thickness")
					: List.of("operation.spacing", "operation.thickness")) {
				object.withArray("drivers").addObject().put("target", target).put("mode", "add")
						.putObject("expression").put("type", "input").put("source", "bass-level");
			}
			assertThat(this.validator.validateAndNormalize(document).path("objects").get(0).path("drivers"))
					.hasSize(shape.equals("box-frame") ? 4 : 2);
			((ObjectNode) object.path("drivers").get(0)).put("target", "operation.iterations");
			assertInvalid(document, "sceneData.objects[0].drivers[0].target");
			((ObjectNode) object.path("drivers").get(0)).put("target", "operation.radius");
			assertInvalid(document, "sceneData.objects[0].drivers[0].target");
		}
	}

	@Test
	void preservesSupportedFrameRotationOrdersAndRejectsUnknownOrders() {
		for (String order : List.of("xyz", "xzy", "yxz", "yzx", "zxy", "zyx")) {
			ObjectNode document = builder("box-frame");
			ObjectNode object = (ObjectNode) document.path("objects").get(0);
			object.putObject("frame").put("rotationOrder", order).putObject("rotation").put("x", 0.1).put("y", 0.2).put("z", 0.3);
			ObjectNode composition = document.putObject("composition").put("version", 1);
			composition.putArray("roots").add("outer");
			ObjectNode group = composition.putArray("groups").addObject().put("id", "outer");
			group.putArray("children").add("shape");
			group.putObject("transform").put("rotationOrder", order);
			JsonNode normalized = this.validator.validateAndNormalize(document);
			assertThat(normalized.path("objects").get(0).path("frame").path("rotationOrder").asText()).isEqualTo(order);
			assertThat(normalized.path("composition").path("groups").get(0).path("transform").path("rotationOrder").asText()).isEqualTo(order);
			assertThat(this.validator.validateAndNormalize(normalized)).isEqualTo(normalized);
			((ObjectNode) group.path("transform")).put("rotationOrder", "xxy");
			assertInvalid(document, "sceneData.composition.groups[0].transform.rotationOrder");
			((ObjectNode) group.path("transform")).put("rotationOrder", order);
			((ObjectNode) object.path("frame")).put("rotationOrder", "zyx();NEVER_EXECUTE()");
			assertInvalid(document, "sceneData.objects[0].frame.rotationOrder");
		}
		ObjectNode withoutOrder = this.validator.validateAndNormalize(builder("box-frame")).deepCopy();
		assertThat(withoutOrder.path("objects").get(0).has("frame")).isFalse();
	}

	@Test
	void proceduralGeometryUsesTheExistingWorkBudgetWithoutPretendingEveryCellIsAnObject() {
		ObjectNode document = builder("lattice");
		operation(document).put("iterations", 5);
		ObjectNode object = (ObjectNode) document.path("objects").get(0);
		object.withArray("arrangements").addObject().put("type", "linear").put("count", 8).put("spacing", 1).put("axis", "x");
		object.withArray("modifiers").addObject().put("type", "shell").put("thickness", 0.1);
		// Eight procedural primitives cost 8 * (3 frame evaluations + 5 folds + 2 shell operations) = 80.
		assertThat(this.validator.validateAndNormalize(document).path("objects")).hasSize(1);
		object.withArray("modifiers").addObject().put("type", "expand").put("amount", 0.1);
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document))
				.isInstanceOfSatisfying(InvalidSceneDataException.class, error -> assertThat(error.getDetails())
						.containsEntry("sceneData.objects", "modifierOperations requires 88; maximum is 80."));
	}

	@Test
	void frameCostsAndFractionalLatticeFoldsCountEveryArrangedCopy() {
		ObjectNode frame = builder("box-frame");
		ObjectNode object = (ObjectNode) frame.path("objects").get(0);
		object.withArray("arrangements").addObject().put("type", "linear").put("count", 8).put("axis", "x").put("spacing", 1);
		object.withArray("arrangements").addObject().put("type", "radial").put("count", 3).put("axis", "y").put("radius", 1);
		assertInvalid(frame, "sceneData.objects"); // The ordinary sixteen-primitive limit still applies.
		object.withArray("arrangements").remove(1);
		object.withArray("modifiers").addObject().put("type", "twist").put("axis", "x").put("amount", 1);
		ObjectNode second = object.deepCopy().put("id", "second");
		frame.withArray("objects").add(second);
		// Sixteen frame primitives with twist consume 16 * (3 + 2) = 80.
		assertThat(this.validator.validateContract(frame).path("objects")).hasSize(2);
		second.withArray("modifiers").addObject().put("type", "expand").put("amount", 0.1);
		assertThatThrownBy(() -> this.validator.validateContract(frame))
				.isInstanceOfSatisfying(InvalidSceneDataException.class, error -> assertThat(error.getDetails())
						.containsEntry("sceneData.objects", "modifierOperations requires 88; maximum is 80."));

		ObjectNode lattice = builder("lattice");
		operation(lattice).put("iterations", 1.99);
		ObjectNode grid = (ObjectNode) lattice.path("objects").get(0);
		grid.withArray("arrangements").addObject().put("type", "linear").put("count", 8).put("axis", "x").put("spacing", 1);
		grid.withArray("modifiers").addObject().put("type", "expand").put("amount", 0.1);
		lattice.withArray("objects").add(grid.deepCopy().put("id", "grid-2"));
		// 1.99 executes one mirror fold, so sixteen copies cost 16 * (3 + 1 + 1) = 80.
		assertThat(this.validator.validateContract(lattice).path("objects")).hasSize(2);
		operation(lattice).put("iterations", 2);
		assertThatThrownBy(() -> this.validator.validateContract(lattice))
				.isInstanceOfSatisfying(InvalidSceneDataException.class, error -> assertThat(error.getDetails())
						.containsEntry("sceneData.objects", "modifierOperations requires 88; maximum is 80."));
	}

	private ObjectNode builder(String type) {
		ObjectNode document = this.json.createObjectNode().put("schemaVersion", 1).put("kind", "builder").put("builderVersion", 1);
		document.putArray("objects").addObject().put("id", "shape").putObject("operation").put("type", type);
		return document;
	}
	private ObjectNode operation(ObjectNode document) { return (ObjectNode) document.path("objects").get(0).path("operation"); }
	private void assertInvalid(JsonNode document, String path) {
		assertThatThrownBy(() -> this.validator.validateAndNormalize(document))
				.isInstanceOfSatisfying(InvalidSceneDataException.class, error -> assertThat(error.getDetails()).containsKey(path));
	}
}
