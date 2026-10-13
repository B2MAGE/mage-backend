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
			"material.metalness", "material.shininess", "material.color.r", "material.color.g", "material.color.b");
	private static final Set<String> GROUP_TARGETS = Set.of("position.x", "position.y", "position.z",
			"rotation.x", "rotation.y", "rotation.z", "scale");
	private static final Map<String, Set<String>> SHAPE_TARGETS = Map.of(
			"sphere", Set.of("operation.radius"),
			"box", Set.of("operation.width", "operation.height", "operation.depth"),
			"torus", Set.of("operation.radius", "operation.tube"),
			"cylinder", Set.of("operation.radius", "operation.height"),
			"box-frame", Set.of("operation.width", "operation.height", "operation.depth", "operation.thickness"),
			"lattice", Set.of("operation.spacing", "operation.thickness"));

	record Workload(int drivers, int driverOperations, int liveUniforms) {}
	private enum ValueType { SCALAR, VECTOR }
	private record Expression(ValueType type, int nodes, int operations, int uniforms) {}

	static Workload validate(JsonNode document, JsonNode limits, JsonNode costs) {
		Set<String> signals = new HashSet<>();
		int drivers = 0;
		int operations = 0;
		int uniforms = 0;
		int noiseCost = limit(costs, "noise");
		for (int index = 0; index < document.path("objects").size(); index++) {
			JsonNode object = document.path("objects").get(index);
			int copies = 1;
			for (JsonNode arrangement : object.path("arrangements")) copies *= arrangement.path("count").intValue();
			Workload work = validateDrivers(object, "sceneData.objects[" + index + "]", false, limits, noiseCost, signals);
			drivers += work.drivers();
			operations += copies * work.driverOperations();
			uniforms += work.liveUniforms();
		}
		for (int index = 0; index < document.path("composition").path("groups").size(); index++) {
			JsonNode group = document.path("composition").path("groups").get(index);
			Workload work = validateDrivers(group, "sceneData.composition.groups[" + index + "]", true, limits, noiseCost, signals);
			drivers += work.drivers();
			operations += work.driverOperations();
			uniforms += work.liveUniforms();
		}
		return new Workload(drivers, operations, uniforms + signals.size());
	}

	private static Workload validateDrivers(JsonNode owner, String ownerPath, boolean group, JsonNode limits, int noiseCost, Set<String> signals) {
		int operations = 0;
		int uniforms = 0;
		JsonNode drivers = owner.path("drivers");
		for (int index = 0; index < drivers.size(); index++) {
			JsonNode driver = drivers.get(index);
			String path = ownerPath + ".drivers[" + index + "]";
			String target = driver.path("target").asText();
			boolean validTarget = group ? GROUP_TARGETS.contains(target) : OBJECT_TARGETS.contains(target)
					|| SHAPE_TARGETS.getOrDefault(owner.path("operation").path("type").asText(), Set.of()).contains(target);
			if (group && target.equals("blend")) validTarget = owner.path("operation").path("type").asText().equals("smooth-union");
			if (group && target.startsWith("material.")) validTarget = owner.has("material");
			if (target.equals("modifier.expand.amount")) validTarget = hasModifier(owner, "expand");
			if (target.equals("modifier.shell.thickness")) validTarget = hasModifier(owner, "shell");
			if (!validTarget) invalid(path + ".target", "Driver target is not available on this object or group.");
			Expression expression = expression(driver.path("expression"), path + ".expression", 1, limits, noiseCost, signals);
			if (expression.type() != ValueType.SCALAR) invalid(path + ".expression", "A property driver must produce a scalar value.");
			if (expression.nodes() > limit(limits, "expressionNodes")) invalid(path + ".expression", "Driver expression exceeds the node limit.");
			operations += expression.operations();
			uniforms += expression.uniforms();
		}
		return new Workload(drivers.size(), operations, uniforms);
	}

	private static boolean hasModifier(JsonNode owner, String type) {
		for (JsonNode modifier : owner.path("modifiers")) {
			if (modifier.path("type").asText().equals(type)) return true;
		}
		return false;
	}

	private static Expression expression(JsonNode node, String path, int depth, JsonNode limits, int noiseCost, Set<String> signals) {
		if (depth > limit(limits, "expressionDepth")) invalid(path, "Driver expression exceeds the depth limit.");
		String type = node.path("type").asText();
		return switch (type) {
			case "constant" -> new Expression(node.path("value").isObject() ? ValueType.VECTOR : ValueType.SCALAR,
					1, 1, node.path("value").isObject() ? 3 : 1);
			case "input" -> {
				String source = node.path("source").asText();
				if (node.has("ancestor") && !source.equals("group-position")) {
					invalid(path + ".ancestor", "Ancestor selection is only available for parent group position.");
				}
				// Native scalar scene inputs still declare shader input slots. Scene
				// pointer components reuse the existing built-in mouse vector.
				if (!source.equals("time") && !VECTOR_INPUTS.contains(source)) signals.add(source);
				yield new Expression(VECTOR_INPUTS.contains(source) ? ValueType.VECTOR : ValueType.SCALAR, 1, 1, 0);
			}
			case "add", "multiply" -> {
				Expression left = expression(node.path("left"), path + ".left", depth + 1, limits, noiseCost, signals);
				Expression right = expression(node.path("right"), path + ".right", depth + 1, limits, noiseCost, signals);
				if (type.equals("add") && left.type() != right.type()) invalid(path, "Addition requires matching scalar or vector types.");
				yield new Expression(left.type() == ValueType.VECTOR || right.type() == ValueType.VECTOR ? ValueType.VECTOR : ValueType.SCALAR,
						1 + left.nodes() + right.nodes(), 1 + left.operations() + right.operations(), left.uniforms() + right.uniforms());
			}
			case "sin", "cos", "clamp", "component" -> {
				Expression input = expression(node.path("input"), path + ".input", depth + 1, limits, noiseCost, signals);
				if (type.equals("component") && input.type() != ValueType.VECTOR) invalid(path + ".input", "A component requires a vector input.");
				if (type.equals("clamp") && node.path("min").doubleValue() > node.path("max").doubleValue()) invalid(path + ".max", "Clamp maximum must be at least its minimum.");
				yield new Expression(type.equals("component") ? ValueType.SCALAR : input.type(),
						1 + input.nodes(), 1 + input.operations(), input.uniforms() + (type.equals("clamp") ? 2 : 0));
			}
			case "noise" -> {
				Expression input = expression(node.path("input"), path + ".input", depth + 1, limits, noiseCost, signals);
				if (input.type() != ValueType.VECTOR) invalid(path + ".input", "Noise requires a vector input.");
				yield new Expression(ValueType.SCALAR, 1 + input.nodes(), noiseCost + input.operations(), input.uniforms());
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
