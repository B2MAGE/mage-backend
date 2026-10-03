package com.bdmage.mage_backend.validation;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Validates the checked-in scene-v1 contract without executing or resolving source.
 * This is a deliberately scoped schema interpreter, not a general JSON Schema engine.
 * Unknown schema keywords fail at startup rather than silently weakening validation.
 */
public final class SceneDocumentValidator {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final Set<String> KEYWORDS = Set.of("$schema", "$id", "$ref", "$defs", "title", "description",
			"oneOf", "anyOf", "not", "type", "const", "enum", "properties", "required", "additionalProperties",
			"propertyNames", "items", "minimum", "maximum", "pattern", "minLength", "maxLength", "default");
	private static final JsonNode SCHEMA = load("scene-v1.schema.json");
	private static final JsonNode CATALOG = load("template-catalog.v1.json");
	private static final Set<String> TEMPLATE_PAIRS = catalogPairs();
	private final SceneSubmissionValidator resources = new SceneSubmissionValidator();

	static {
		verifySchema(SCHEMA);
		if (!SCHEMA.path("oneOf").equals(JSON.createArrayNode()
				.add(JSON.createObjectNode().put("$ref", "#/$defs/template"))
				.add(JSON.createObjectNode().put("$ref", "#/$defs/custom")))) {
			throw new IllegalStateException("Unexpected scene document discriminators.");
		}
	}

	/** The write boundary: contract validation, canonical defaults, then operational limits. */
	public JsonNode validateAndNormalize(JsonNode document) {
		// Bound traversal and serialization before recursively reading any contract fields.
		this.resources.validateTransportBudget(document);
		JsonNode normalized = validateContract(document);
		// Defaults count toward stored document size/depth just like submitted fields.
		this.resources.validateTransportBudget(normalized);
		if (normalized.path("kind").textValue().equals("custom")) {
			try {
				this.resources.validate(normalized.path("scene"));
			} catch (InvalidSceneDataException exception) {
				Map<String, String> details = new LinkedHashMap<>();
				exception.getDetails().forEach((path, message) ->
						details.put("sceneData.scene" + path.substring("sceneData".length()), message));
				throw new InvalidSceneDataException(details);
			}
		}
		return normalized;
	}

	/**
	 * Structural conformance only, for shared transport fixtures. A valid custom
	 * envelope is not necessarily a valid submission and never authorizes execution.
	 */
	public JsonNode validateContract(JsonNode document) {
		if (document == null || !document.isObject()) invalid("sceneData", "Must be a scene document object.");
		if (!document.has("schemaVersion")) invalid("sceneData.schemaVersion", "This field is required.");
		if (!sameValue(document.path("schemaVersion"), JSON.getNodeFactory().numberNode(1))) {
			invalid("sceneData.schemaVersion", "Unsupported scene schema version.");
		}
		if (!document.has("kind")) invalid("sceneData.kind", "This field is required.");
		String kind = document.path("kind").isTextual() ? document.path("kind").textValue() : "";
		if (!kind.equals("template") && !kind.equals("custom")) invalid("sceneData.kind", "Must be template or custom.");
		JsonNode normalized = validate(document, SCHEMA.path("$defs").path(kind), "sceneData");
		if (kind.equals("template")) {
			String pair = normalized.path("templateId").textValue() + ":" + normalized.path("templateVersion").intValue();
			if (!TEMPLATE_PAIRS.contains(pair)) invalid("sceneData.templateId", "Unknown template ID and version.");
		}
		return normalized;
	}

	private JsonNode validate(JsonNode value, JsonNode rule, String path) {
		if (rule.has("$ref")) return validate(value, resolve(rule.path("$ref").textValue()), path);
		if (rule.has("anyOf")) {
			InvalidSceneDataException deepest = null;
			for (JsonNode alternative : rule.path("anyOf")) {
				try { return validate(value, alternative, path); }
				catch (InvalidSceneDataException exception) {
					if (deepest == null || errorPathLength(exception) > errorPathLength(deepest)) deepest = exception;
				}
			}
			if (deepest != null) throw deepest;
			invalid(path, "Must contain ordinary finite JSON values without prototype fields.");
		}
		if (rule.has("not")) {
			boolean matches;
			try { validate(value, rule.path("not"), path); matches = true; }
			catch (InvalidSceneDataException ignored) { matches = false; }
			if (matches) invalid(path, "Prototype-related fields are not allowed.");
		}
		if (rule.has("const") && !sameValue(value, rule.path("const"))) invalid(path, "Unsupported value.");
		if (rule.has("enum")) {
			boolean matches = false;
			for (JsonNode option : rule.path("enum")) if (sameValue(value, option)) { matches = true; break; }
			if (!matches) invalid(path, "Unsupported value.");
		}
		if (rule.has("type")) {
			JsonNode types = rule.path("type");
			boolean matches = types.isTextual() && matchesType(value, types.textValue());
			if (types.isArray()) for (JsonNode type : types) matches |= matchesType(value, type.textValue());
			if (!matches) invalid(path, "Incorrect JSON value type.");
		}
		if (value.isObject()) {
			ObjectNode result = JSON.createObjectNode();
			for (JsonNode required : rule.path("required")) {
				if (!value.has(required.textValue())) invalid(fieldPath(path, required.textValue()), "This field is required.");
			}
			var fields = value.fields();
			while (fields.hasNext()) {
				var field = fields.next();
				String childPath = fieldPath(path, field.getKey());
				if (rule.has("propertyNames")) validate(TextNode.valueOf(field.getKey()), rule.path("propertyNames"), childPath);
				JsonNode childRule = rule.path("properties").get(field.getKey());
				if (childRule == null) {
					JsonNode additional = rule.get("additionalProperties");
					if (additional != null && additional.isBoolean() && !additional.booleanValue()) invalid(childPath, "Unknown field is not allowed.");
					childRule = additional != null && additional.isObject() ? additional : JSON.createObjectNode();
				}
				result.set(field.getKey(), validate(field.getValue(), childRule, childPath));
			}
			var properties = rule.path("properties").fields();
			while (properties.hasNext()) {
				var property = properties.next();
				if (!value.has(property.getKey()) && property.getValue().has("default")) {
					result.set(property.getKey(), validate(property.getValue().path("default"), property.getValue(), fieldPath(path, property.getKey())));
				}
			}
			return result;
		}
		if (value.isArray()) {
			var result = JSON.createArrayNode();
			for (int index = 0; index < value.size(); index++) result.add(validate(value.get(index), rule.path("items"), path + "[" + index + "]"));
			return result;
		}
		if (value.isNumber()) {
			if (!Double.isFinite(value.doubleValue())) invalid(path, "Must be a finite number.");
			if (rule.has("minimum") && value.decimalValue().compareTo(rule.path("minimum").decimalValue()) < 0) invalid(path, "Value is below the minimum.");
			if (rule.has("maximum") && value.decimalValue().compareTo(rule.path("maximum").decimalValue()) > 0) invalid(path, "Value exceeds the maximum.");
		}
		if (value.isTextual()) {
			String text = value.textValue();
			int length = text.codePointCount(0, text.length());
			if (rule.has("minLength") && length < rule.path("minLength").intValue()) invalid(path, "Text is too short.");
			if (rule.has("maxLength") && length > rule.path("maxLength").intValue()) invalid(path, "Text is too long.");
			if (rule.has("pattern") && !Pattern.compile(rule.path("pattern").textValue()).matcher(text).find()) invalid(path, "Text does not match the required format.");
		}
		if (!value.isValueNode() || value.isPojo() || value.isBinary() || value.isMissingNode()) invalid(path, "Must contain JSON values only.");
		return value.deepCopy();
	}

	private static boolean matchesType(JsonNode value, String type) {
		return switch (type) {
			case "object" -> value.isObject();
			case "array" -> value.isArray();
			case "null" -> value.isNull();
			case "boolean" -> value.isBoolean();
			case "string" -> value.isTextual();
			case "number" -> value.isNumber() && Double.isFinite(value.doubleValue());
			case "integer" -> value.isNumber() && Double.isFinite(value.doubleValue()) && value.decimalValue().stripTrailingZeros().scale() <= 0;
			default -> throw new IllegalStateException("Unsupported scene contract type.");
		};
	}

	private static boolean sameValue(JsonNode left, JsonNode right) {
		if (left.isNumber() && right.isNumber()) return Double.isFinite(left.doubleValue()) && Double.isFinite(right.doubleValue())
				&& left.decimalValue().compareTo(right.decimalValue()) == 0;
		return left.equals(right);
	}

	private static JsonNode resolve(String reference) {
		if (reference == null || !reference.startsWith("#/$defs/")) throw new IllegalStateException("Unsupported scene contract reference.");
		JsonNode result = SCHEMA.at(reference.substring(1));
		if (!result.isObject()) throw new IllegalStateException("Missing scene contract definition.");
		return result;
	}

	private static void verifySchema(JsonNode rule) {
		if (!rule.isObject()) throw new IllegalStateException("Scene contract rules must be objects.");
		if (rule.has("oneOf") && rule != SCHEMA) throw new IllegalStateException("Nested scene contract oneOf is unsupported.");
		var names = rule.fieldNames();
		while (names.hasNext()) if (!KEYWORDS.contains(names.next())) throw new IllegalStateException("Unsupported scene contract keyword.");
		if (rule.has("$ref")) {
			resolve(rule.path("$ref").textValue());
			if (rule.size() != 1) throw new IllegalStateException("Scene contract reference siblings are unsupported.");
		}
		for (String group : Set.of("$defs", "properties")) for (JsonNode child : rule.path(group)) verifySchema(child);
		for (String group : Set.of("oneOf", "anyOf")) for (JsonNode child : rule.path(group)) verifySchema(child);
		for (String name : Set.of("not", "items", "propertyNames")) if (rule.has(name)) verifySchema(rule.path(name));
		if (rule.path("additionalProperties").isObject()) verifySchema(rule.path("additionalProperties"));
		if (rule.has("type")) {
			JsonNode types = rule.path("type");
			if (types.isTextual()) matchesType(JSON.getNodeFactory().nullNode(), types.textValue());
			else if (types.isArray()) for (JsonNode type : types) matchesType(JSON.getNodeFactory().nullNode(), type.textValue());
			else throw new IllegalStateException("Invalid scene contract type.");
		}
	}

	private static Set<String> catalogPairs() {
		if (CATALOG.path("catalogVersion").asInt() != 1 || !CATALOG.path("templates").isArray()) throw new IllegalStateException("Unsupported template catalog.");
		Set<String> pairs = new HashSet<>();
		for (JsonNode entry : CATALOG.path("templates")) {
			String id = entry.path("templateId").textValue();
			if (id == null || !entry.path("templateVersion").isIntegralNumber() || entry.path("templateVersion").intValue() != 1
					|| !pairs.add(id + ":1")) throw new IllegalStateException("Invalid template catalog entry.");
		}
		Set<String> schemaPairs = new HashSet<>();
		for (JsonNode id : SCHEMA.at("/$defs/template/properties/templateId/enum")) schemaPairs.add(id.textValue() + ":1");
		if (!pairs.equals(schemaPairs)) throw new IllegalStateException("Template catalog and schema disagree.");
		return Set.copyOf(pairs);
	}

	private static JsonNode load(String name) {
		try (var input = SceneDocumentValidator.class.getResourceAsStream("/contracts/scenes/" + name)) {
			if (input == null) throw new IllegalStateException("Scene contract resource is missing.");
			return JSON.readTree(input);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot load scene contract resource.", exception);
		}
	}

	private static String fieldPath(String parent, String name) {
		return name.length() <= SceneLimits.KEY_BYTES && FIELD_NAME.matcher(name).matches() ? parent + "." + name : parent;
	}

	private static int errorPathLength(InvalidSceneDataException exception) {
		return exception.getDetails().keySet().stream().mapToInt(String::length).max().orElse(0);
	}

	private static void invalid(String path, String message) {
		throw new InvalidSceneDataException(Map.of(path, message));
	}
}
