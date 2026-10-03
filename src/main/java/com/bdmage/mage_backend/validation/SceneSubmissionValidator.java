package com.bdmage.mage_backend.validation;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Validates submitted data only. Shader source remains opaque and untrusted. */
public final class SceneSubmissionValidator {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
	private static final Set<String> PROTOTYPE_KEYS = Set.of("__proto__", "prototype", "constructor");
	private final JsonNode policy = SceneLimits.policy();

	public void validate(JsonNode sceneData) {
		if (sceneData == null || !sceneData.isObject()) {
			invalid("sceneData", "Must be a JSON object.");
		}
		validateStructure(sceneData);
		validateBytes(sceneData);
		validateRule(sceneData, this.policy.path("scene"), "sceneData");
		validateEffectBudget(sceneData);
	}

	private void validateStructure(JsonNode root) {
		ArrayDeque<Entry> pending = new ArrayDeque<>();
		pending.add(new Entry(root, "sceneData", 1));
		int keys = 0;
		int nodes = 0;
		while (!pending.isEmpty()) {
			Entry entry = pending.removeLast();
			JsonNode node = entry.value();
			if (++nodes > SceneLimits.TOTAL_NODES) {
				invalid("sceneData", "Too many JSON values; maximum is " + SceneLimits.TOTAL_NODES + ".");
			}
			if (node.isContainerNode() && entry.depth() > SceneLimits.SCENE_DEPTH) {
				invalid(entry.path(), "JSON nesting exceeds " + SceneLimits.SCENE_DEPTH + " levels.");
			}
			if (node.isObject()) {
				if (node.size() > SceneLimits.OBJECT_KEYS) {
					invalid(entry.path(), "Too many object fields; maximum is " + SceneLimits.OBJECT_KEYS + ".");
				}
				keys += node.size();
				if (keys > SceneLimits.TOTAL_KEYS) {
					invalid("sceneData", "Too many total object fields; maximum is " + SceneLimits.TOTAL_KEYS + ".");
				}
				var fields = node.fields();
				while (fields.hasNext()) {
					var field = fields.next();
					String name = field.getKey();
					if (name.length() > SceneLimits.KEY_BYTES || utf8Bytes(name) > SceneLimits.KEY_BYTES) {
						invalid(entry.path(), "Field names must not exceed " + SceneLimits.KEY_BYTES + " UTF-8 bytes.");
					}
					String path = fieldPath(entry.path(), name);
					if (PROTOTYPE_KEYS.contains(name)) {
						invalid(path, "Prototype-related fields are not allowed.");
					}
					pending.add(new Entry(field.getValue(), path, entry.depth() + 1));
				}
			} else if (node.isArray()) {
				if (node.size() > SceneLimits.ARRAY_ITEMS) {
					invalid(entry.path(), "Too many array items; maximum is " + SceneLimits.ARRAY_ITEMS + ".");
				}
				for (int index = 0; index < node.size(); index++) {
					pending.add(new Entry(node.get(index), entry.path() + "[" + index + "]", entry.depth() + 1));
				}
			} else if (node.isNumber() && !Double.isFinite(node.doubleValue())) {
				invalid(entry.path(), "Must be a finite number.");
			} else if (!node.isValueNode() || node.isPojo() || node.isBinary()) {
				invalid(entry.path(), "Must contain JSON values only.");
			}
		}
	}

	private void validateBytes(JsonNode sceneData) {
		try {
			JSON.writeValue(new LimitedOutputStream(SceneLimits.SCENE_BYTES), sceneData);
		} catch (IOException exception) {
			invalid("sceneData", "Serialized scene must not exceed " + SceneLimits.SCENE_BYTES + " UTF-8 bytes.");
		}
	}

	private void validateRule(JsonNode value, JsonNode rule, String path) {
		switch (rule.path("type").asText()) {
			case "object" -> {
				if (!value.isObject()) {
					invalid(path, "Must be an object.");
				}
				JsonNode allowed = rule.path("fields");
				var fields = value.fields();
				while (fields.hasNext()) {
					var field = fields.next();
					String childPath = fieldPath(path, field.getKey());
					JsonNode childRule = allowed.get(field.getKey());
					if (childRule == null) {
						invalid(childPath, "Unknown field is not allowed.");
					}
					validateRule(field.getValue(), childRule, childPath);
				}
				for (JsonNode required : rule.path("required")) {
					if (!value.has(required.asText())) {
						invalid(path + "." + required.asText(), "This field is required.");
					}
				}
			}
			case "array" -> {
				int maxItems = rule.path("maxItems").asInt(SceneLimits.ARRAY_ITEMS);
				if (!value.isArray() || value.size() > maxItems) {
					invalid(path, "Must be an array with at most " + maxItems + " items.");
				}
				Set<JsonNode> seen = new HashSet<>();
				for (int index = 0; index < value.size(); index++) {
					JsonNode item = value.get(index);
					String itemPath = path + "[" + index + "]";
					validateRule(item, rule.path("items"), itemPath);
					JsonNode identity = rule.has("uniqueBy") ? item.path(rule.path("uniqueBy").asText()) : item;
					if ((rule.path("unique").asBoolean() || rule.has("uniqueBy")) && !seen.add(identity)) {
						invalid(itemPath, "Duplicate items are not allowed.");
					}
				}
			}
			case "number", "integer" -> {
				if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
					invalid(path, "Must be a finite number.");
				}
				if (rule.path("type").asText().equals("integer") && value.decimalValue().stripTrailingZeros().scale() > 0) {
					invalid(path, "Must be a whole number.");
				}
				if (value.decimalValue().compareTo(rule.path("minimum").decimalValue()) < 0
						|| value.decimalValue().compareTo(rule.path("maximum").decimalValue()) > 0) {
					invalid(path, "Must be between " + rule.path("minimum") + " and " + rule.path("maximum") + ".");
				}
			}
			case "boolean" -> {
				if (!value.isBoolean()) {
					invalid(path, "Must be a boolean.");
				}
			}
			case "enum" -> {
				boolean supported = false;
				for (JsonNode option : rule.path("values")) {
					if (option.equals(value) || (option.isNumber() && value.isNumber()
							&& option.decimalValue().compareTo(value.decimalValue()) == 0)) {
						supported = true;
						break;
					}
				}
				if (!supported) {
					invalid(path, "Unsupported value.");
				}
			}
			case "color" -> {
				if (!value.isTextual() || !COLOR.matcher(value.textValue()).matches()) {
					invalid(path, "Must be a six-digit hexadecimal color beginning with #.");
				}
			}
			case "source" -> {
				if (!value.isTextual() || value.textValue().isBlank()) {
					invalid(path, "Must be nonblank shader source text.");
				}
				String source = value.textValue();
				if (source.length() > SceneLimits.SOURCE_BYTES || utf8Bytes(source) > SceneLimits.SOURCE_BYTES) {
					invalid(path, "Shader source must not exceed " + SceneLimits.SOURCE_BYTES + " UTF-8 bytes.");
				}
				for (int index = 0; index < source.length(); index++) {
					char character = source.charAt(index);
					if (Character.isHighSurrogate(character)) {
						if (++index >= source.length() || !Character.isLowSurrogate(source.charAt(index))) {
							invalid(path, "Shader source must contain valid Unicode text.");
						}
					} else if (Character.isLowSurrogate(character)) {
						invalid(path, "Shader source must contain valid Unicode text.");
					}
				}
			}
			default -> throw new IllegalStateException("Unknown validation policy rule.");
		}
	}

	private void validateEffectBudget(JsonNode sceneData) {
		JsonNode fx = sceneData.path("fx");
		int count = fx.path("bloom").path("enabled").asBoolean(false) ? 1 : 0;
		for (JsonNode flag : this.policy.path("optionalEffectFlags")) {
			if (fx.path("passes").path(flag.asText()).asBoolean(false)) {
				count++;
			}
		}
		if (count > SceneLimits.OPTIONAL_EFFECTS) {
			invalid("sceneData.fx", "Enable at most " + SceneLimits.OPTIONAL_EFFECTS + " optional effects, including bloom.");
		}
	}

	private static String fieldPath(String parent, String name) {
		return FIELD_NAME.matcher(name).matches() ? parent + "." + name : parent;
	}

	private static int utf8Bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8).length;
	}

	private static void invalid(String path, String message) {
		throw new InvalidSceneDataException(Map.of(path, message));
	}

	private record Entry(JsonNode value, String path, int depth) {
	}

	private static final class LimitedOutputStream extends OutputStream {
		private final int limit;
		private int count;

		private LimitedOutputStream(int limit) {
			this.limit = limit;
		}

		@Override
		public void write(int value) throws IOException {
			increment(1);
		}

		@Override
		public void write(byte[] values, int offset, int length) throws IOException {
			increment(length);
		}

		private void increment(int length) throws IOException {
			if (length > this.limit - this.count) {
				throw new IOException("Scene byte limit exceeded.");
			}
			this.count += length;
		}
	}
}
