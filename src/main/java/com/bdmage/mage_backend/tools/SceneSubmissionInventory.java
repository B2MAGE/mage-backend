package com.bdmage.mage_backend.tools;

import com.bdmage.mage_backend.exception.InvalidSceneDataException;
import com.bdmage.mage_backend.validation.SceneSubmissionValidator;
import com.bdmage.mage_backend.validation.SceneDocumentValidator;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PushbackReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Offline, read-only inventory. This main method never starts Spring or connects to a database. */
public final class SceneSubmissionInventory {

	static final int MAX_INPUT_BYTES = 64 * 1024 * 1024;
	static final int MAX_SCENES = 10_000;
	static final int MAX_FAILURE_DETAILS = 100;
	private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
			.streamReadConstraints(StreamReadConstraints.builder()
					.maxNestingDepth(128)
					.maxStringLength(MAX_INPUT_BYTES)
					.build())
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.build());

	private SceneSubmissionInventory() {
	}

	public static void main(String[] args) {
		System.exit(run(args, System.out));
	}

	static int run(String[] args, PrintStream output) {
		try {
			if (args.length != 1) throw new IllegalArgumentException();
			JsonNode input = readInput(Path.of(args[0]));
			Map<String, Object> report = inspect(input);
			output.println(JSON.writeValueAsString(report));
			return ((Number) report.get("invalidScenes")).intValue() == 0 ? 0 : 1;
		} catch (IOException | IllegalArgumentException ex) {
			// Parser exceptions can quote submitted source. Never print exception text or payloads.
			output.println("{\"error\":\"INVALID_INVENTORY_INPUT\",\"message\":\"Provide one readable UTF-8 JSON array of at most 10000 scene records and 64 MiB. Each record requires sceneData.\"}");
			return 2;
		}
	}

	static JsonNode readInput(Path path) throws IOException {
		if (!Files.isRegularFile(path) || Files.size(path) > MAX_INPUT_BYTES) {
			throw new IllegalArgumentException();
		}
		try (InputStream stream = Files.newInputStream(path)) {
			byte[] bytes = stream.readNBytes(MAX_INPUT_BYTES + 1);
			if (bytes.length > MAX_INPUT_BYTES) throw new IllegalArgumentException();
			// A Reader prevents Jackson's byte parser from auto-detecting UTF-16/32.
			try (var reader = new PushbackReader(new InputStreamReader(new ByteArrayInputStream(bytes),
					StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
							.onUnmappableCharacter(CodingErrorAction.REPORT)), 1)) {
				int first = reader.read();
				if (first != -1 && first != '\uFEFF') reader.unread(first);
				try (var parser = JSON.createParser(reader)) {
					JsonNode input = JSON.readTree(parser);
					if (input == null || parser.nextToken() != null) throw new IllegalArgumentException();
					return input;
				}
			}
		}
	}

	static Map<String, Object> inspect(JsonNode records) throws IOException {
		if (!records.isArray() || records.size() > MAX_SCENES) throw new IllegalArgumentException();
		SceneSubmissionValidator validator = new SceneSubmissionValidator();
		SceneDocumentValidator documents = new SceneDocumentValidator();
		List<Map<String, Object>> failures = new ArrayList<>();
		TreeSet<String> topLevelFields = new TreeSet<>();
		int invalid = 0;
		int maxSceneBytes = 0;
		int maxSourceBytes = 0;
		int maxContainerDepth = 0;
		int maxEnabledEffects = 0;
		for (int index = 0; index < records.size(); index++) {
			JsonNode record = records.get(index);
			if (!record.isObject() || !record.has("sceneData")) throw new IllegalArgumentException();
			JsonNode sceneData = record.get("sceneData");
			JsonNode engineData = "custom".equals(sceneData.path("kind").asText())
					? sceneData.path("scene") : sceneData;
			maxSceneBytes = Math.max(maxSceneBytes, JSON.writeValueAsBytes(sceneData).length);
			JsonNode source = engineData.path("visualizer").path("shader");
			if (source.isTextual()) {
				maxSourceBytes = Math.max(maxSourceBytes, source.textValue().getBytes(StandardCharsets.UTF_8).length);
			}
			maxContainerDepth = Math.max(maxContainerDepth, containerDepth(sceneData));
			maxEnabledEffects = Math.max(maxEnabledEffects, enabledEffects(engineData));
			sceneData.fieldNames().forEachRemaining(field -> {
				if (topLevelFields.size() < 64) {
					topLevelFields.add(field.length() <= 80 && FIELD_NAME.matcher(field).matches() ? field : "<invalid field>");
				}
			});
			try {
				if (sceneData.has("kind") || sceneData.has("schemaVersion") || sceneData.has("templateId") || sceneData.has("builderVersion")) {
					documents.validateAndNormalize(sceneData);
				} else {
					// Historical bare-engine exports remain useful for read-only repair audits.
					validator.validate(sceneData);
				}
			} catch (InvalidSceneDataException ex) {
				invalid++;
				if (failures.size() < MAX_FAILURE_DETAILS) {
					JsonNode id = record.hasNonNull("sceneId") ? record.get("sceneId") : record.path("id");
					String sceneId = id.isTextual() || id.isNumber() ? bounded(id.asText(), 120) : "row-" + (index + 1);
					Map<String, Object> failure = new LinkedHashMap<>();
					failure.put("sceneId", sceneId);
					failure.put("row", index + 1);
					failure.put("code", "INVALID_SCENE_DATA");
					// Values and messages are omitted: the report must not echo shader source or metadata.
					failure.put("fields", ex.getDetails().keySet().stream().limit(20).map(field -> bounded(field, 160)).toList());
					failures.add(failure);
				}
			}
		}
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("scenes", records.size());
		report.put("validScenes", records.size() - invalid);
		report.put("invalidScenes", invalid);
		report.put("maxSceneUtf8Bytes", maxSceneBytes);
		report.put("maxShaderUtf8Bytes", maxSourceBytes);
		report.put("maxContainerDepth", maxContainerDepth);
		report.put("maxEnabledEffectsExcludingOutput", maxEnabledEffects);
		report.put("topLevelFields", topLevelFields);
		report.put("failures", failures);
		report.put("omittedFailureDetails", Math.max(0, invalid - failures.size()));
		return report;
	}

	private static int containerDepth(JsonNode value) {
		if (!value.isContainerNode()) return 0;
		int depth = 0;
		for (JsonNode child : value) depth = Math.max(depth, containerDepth(child));
		return depth + 1;
	}

	private static int enabledEffects(JsonNode data) {
		if ("template".equals(data.path("kind").asText()) || "builder".equals(data.path("kind").asText())) {
			JsonNode settings = data.path("settings");
			int count = (settings.path("bloom").path("enabled").isBoolean() && settings.path("bloom").path("enabled").booleanValue() ? 1 : 0)
					+ (settings.path("tint").path("enabled").isBoolean() && settings.path("tint").path("enabled").booleanValue() ? 1 : 0);
			var fields = settings.path("effects").path("passes").fields();
			while (fields.hasNext()) {
				var pass = fields.next();
				if (!"outputPass".equals(pass.getKey()) && pass.getValue().isBoolean() && pass.getValue().booleanValue()) count++;
			}
			return count;
		}
		JsonNode fx = data.path("fx");
		JsonNode bloom = fx.path("bloom").path("enabled");
		int count = bloom.isBoolean() && bloom.booleanValue() ? 1 : 0;
		var fields = fx.path("passes").fields();
		while (fields.hasNext()) {
			var pass = fields.next();
			if (!"outputPass".equals(pass.getKey()) && pass.getValue().isBoolean() && pass.getValue().booleanValue()) count++;
		}
		return count;
	}

	private static String bounded(String value, int length) {
		return value.length() <= length ? value : value.substring(0, length) + "…";
	}
}
