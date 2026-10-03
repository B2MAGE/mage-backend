package com.bdmage.mage_backend.validation;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** One checked-in policy is shared by transport, persistence validation, and inventory tooling. */
public final class SceneLimits {

	private static final JsonNode POLICY = loadPolicy();

	public static final int REQUEST_BYTES = limit("requestBytes");
	public static final int SCENE_BYTES = limit("sceneBytes");
	public static final int SOURCE_BYTES = limit("sourceBytes");
	public static final int SCENE_DEPTH = limit("sceneDepth");
	public static final int ARRAY_ITEMS = limit("arrayItems");
	public static final int OBJECT_KEYS = limit("objectKeys");
	public static final int TOTAL_KEYS = limit("totalKeys");
	public static final int TOTAL_NODES = limit("totalNodes");
	public static final int KEY_BYTES = limit("keyBytes");
	public static final int OPTIONAL_EFFECTS = limit("optionalEffects");

	private SceneLimits() {
	}

	/** A caller cannot mutate the process-wide validation rules. */
	public static JsonNode policy() {
		return POLICY.deepCopy();
	}

	private static int limit(String name) {
		JsonNode value = POLICY.path("limits").path(name);
		if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() <= 0) {
			throw new IllegalStateException("Invalid scene limit: " + name);
		}
		return value.intValue();
	}

	private static JsonNode loadPolicy() {
		try (InputStream input = SceneLimits.class.getResourceAsStream("/scene-limits.v1.json")) {
			if (input == null) {
				throw new IllegalStateException("Scene limits policy is missing.");
			}
			JsonNode policy = new ObjectMapper().readTree(input);
			if (policy.path("policyVersion").asInt() != 1 || !policy.path("scene").isObject()) {
				throw new IllegalStateException("Unsupported scene limits policy.");
			}
			return policy;
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot load scene limits policy.", exception);
		}
	}
}
