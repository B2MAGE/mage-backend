package com.bdmage.mage_backend.validation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;

/** Independent API checks for typed, closed Builder expressions. Never evaluates submitted code. */
final class BuilderDriverValidator {

	private static final Set<String> VECTOR_INPUTS = Set.of("local-position", "group-position", "world-position", "view-ray", "scene-pointer");
	private static final Set<String> OBJECT_TARGETS = Set.of("position.x", "position.y", "position.z",
			"rotation.x", "rotation.y", "rotation.z", "scale.x", "scale.y", "scale.z",
			"material.metalness", "material.shininess");
	private static final Set<String> GROUP_TARGETS = Set.of("position.x", "position.y", "position.z",
			"rotation.x", "rotation.y", "rotation.z", "scale");
	private static final Map<String, Set<String>> SHAPE_TARGETS = Map.of(
			"sphere", Set.of("operation.radius"),
			"box", Set.of("operation.width", "operation.height", "operation.depth"),
			"torus", Set.of("operation.radius", "operation.tube"),
			"cylinder", Set.of("operation.radius", "operation.height"),
			"box-frame", Set.of("operation.width", "operation.height", "operation.depth", "operation.thickness"),
			"lattice", Set.of("operation.spacing", "operation.thickness"));

	record Workload(int drivers, int driverNodes, int liveUniforms) {}
	private enum ValueType { SCALAR, VECTOR }
	private record Expression(ValueType type, int nodes, int uniforms) {}

	static Workload validate(JsonNode document, JsonNode limits) {
		Set<String> signals = new HashSet<>();
		int drivers = 0;
		int nodes = 0;
		int uniforms = 0;
		for (int index = 0; index < document.path("objects").size(); index++) {
			JsonNode object = document.path("objects").get(index);
			int copies = 1;
			for (JsonNode arrangement : object.path("arrangements")) copies *= arrangement.path("count").intValue();
			Workload work = validateDrivers(object, "sceneData.objects[" + index + "]", false, limits, signals);
			drivers += work.drivers();
			nodes += copies * work.driverNodes();
			uniforms += work.liveUniforms();
		}
		for (int index = 0; index < document.path("composition").path("groups").size(); index++) {
			JsonNode group = document.path("composition").path("groups").get(index);
			Workload work = validateDrivers(group, "sceneData.composition.groups[" + index + "]", true, limits, signals);
			drivers += work.drivers();
			nodes += work.driverNodes();
			uniforms += work.liveUniforms();
		}
		return new Workload(drivers, nodes, uniforms + signals.size());
	}

	private static Workload validateDrivers(JsonNode owner, String ownerPath, boolean group, JsonNode limits, Set<String> signals) {
		int nodes = 0;
		int uniforms = 0;
		JsonNode drivers = owner.path("drivers");
		for (int index = 0; index < drivers.size(); index++) {
			JsonNode driver = drivers.get(index);
			String path = ownerPath + ".drivers[" + index + "]";
			String target = driver.path("target").asText();
			boolean validTarget = group ? GROUP_TARGETS.contains(target) : OBJECT_TARGETS.contains(target)
					|| SHAPE_TARGETS.getOrDefault(owner.path("operation").path("type").asText(), Set.of()).contains(target);
			if (group && target.equals("blend")) validTarget = owner.path("operation").path("type").asText().equals("smooth-union");
			if (group && (target.equals("material.metalness") || target.equals("material.shininess"))) validTarget = owner.has("material");
			if (!validTarget) invalid(path + ".target", "Driver target is not available on this object or group.");
			Expression expression = expression(driver.path("expression"), path + ".expression", 1, limits, signals);
			if (expression.type() != ValueType.SCALAR) invalid(path + ".expression", "A property driver must produce a scalar value.");
			if (expression.nodes() > limit(limits, "expressionNodes")) invalid(path + ".expression", "Driver expression exceeds the node limit.");
			nodes += expression.nodes();
			uniforms += expression.uniforms();
		}
		return new Workload(drivers.size(), nodes, uniforms);
	}

	private static Expression expression(JsonNode node, String path, int depth, JsonNode limits, Set<String> signals) {
		if (depth > limit(limits, "expressionDepth")) invalid(path, "Driver expression exceeds the depth limit.");
		String type = node.path("type").asText();
		return switch (type) {
			case "constant" -> new Expression(node.path("value").isObject() ? ValueType.VECTOR : ValueType.SCALAR,
					1, node.path("value").isObject() ? 3 : 1);
			case "input" -> {
				String source = node.path("source").asText();
				// Native scalar scene inputs still declare shader input slots. Scene
				// pointer components reuse the existing built-in mouse vector.
				if (!source.equals("time") && !VECTOR_INPUTS.contains(source)) signals.add(source);
				yield new Expression(VECTOR_INPUTS.contains(source) ? ValueType.VECTOR : ValueType.SCALAR, 1, 0);
			}
			case "add", "multiply" -> {
				Expression left = expression(node.path("left"), path + ".left", depth + 1, limits, signals);
				Expression right = expression(node.path("right"), path + ".right", depth + 1, limits, signals);
				if (type.equals("add") && left.type() != right.type()) invalid(path, "Addition requires matching scalar or vector types.");
				yield new Expression(left.type() == ValueType.VECTOR || right.type() == ValueType.VECTOR ? ValueType.VECTOR : ValueType.SCALAR,
						1 + left.nodes() + right.nodes(), left.uniforms() + right.uniforms());
			}
			case "sin", "cos", "clamp", "component" -> {
				Expression input = expression(node.path("input"), path + ".input", depth + 1, limits, signals);
				if (type.equals("component") && input.type() != ValueType.VECTOR) invalid(path + ".input", "A component requires a vector input.");
				if (type.equals("clamp") && node.path("min").doubleValue() > node.path("max").doubleValue()) invalid(path + ".max", "Clamp maximum must be at least its minimum.");
				yield new Expression(type.equals("component") ? ValueType.SCALAR : input.type(),
						1 + input.nodes(), input.uniforms() + (type.equals("clamp") ? 2 : 0));
			}
			default -> throw new IllegalStateException("Unsupported Builder expression escaped contract validation.");
		};
	}

	private static int limit(JsonNode limits, String key) {
		int value = limits.path(key).asInt(-1);
		if (value < 0) throw new IllegalStateException("Missing Builder driver policy: " + key);
		return value;
	}

	private static void invalid(String path, String message) {
		throw new InvalidSceneDataException(Map.of(path, message));
	}
}
