package com.bdmage.mage_backend.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneSubmissionInventoryTests {

	private final ObjectMapper mapper = new ObjectMapper();

	@TempDir
	Path temporaryDirectory;

	@Test
	void allVersionOnePresetsAndQualityDemosMeetSubmissionLimitsWithoutExecutingSource() throws Exception {
		Map<String, Object> presets = SceneSubmissionInventory.inspect(fixture("builtin-presets.json"));
		Map<String, Object> demos = SceneSubmissionInventory.inspect(fixture("demo-quality.json"));

		assertThat(presets).containsEntry("scenes", 14).containsEntry("validScenes", 14)
				.containsEntry("invalidScenes", 0).containsEntry("maxShaderUtf8Bytes", 3871)
				.containsEntry("maxContainerDepth", 4).containsEntry("maxEnabledEffectsExcludingOutput", 0);
		assertThat(demos).containsEntry("scenes", 100).containsEntry("validScenes", 100)
				.containsEntry("invalidScenes", 0).containsEntry("maxSceneUtf8Bytes", 3351)
				.containsEntry("maxShaderUtf8Bytes", 2090).containsEntry("maxContainerDepth", 4)
				.containsEntry("maxEnabledEffectsExcludingOutput", 1);
	}

	@Test
	void corpusFilesAndShadersMatchTheirRecordedFingerprints() throws Exception {
		JsonNode provenance = fixture("provenance.json");
		for (String filename : new String[] { "builtin-presets.json", "demo-quality.json" }) {
			String content = fixtureText(filename).replace("\r\n", "\n");
			JsonNode records = this.mapper.readTree(content);
			JsonNode metadata = provenance.path("fixtures").path(filename);
			assertThat(sha256(content)).isEqualTo(metadata.path("fileSha256").textValue());
			assertThat(records.size()).isEqualTo(metadata.path("scenes").intValue());
			for (int index = 0; index < records.size(); index++) {
				JsonNode record = records.get(index);
				JsonNode fingerprint = metadata.path("records").get(index);
				assertThat(record.path("sceneId")).isEqualTo(fingerprint.path("sceneId"));
				assertThat(sha256(record.path("sceneData").path("visualizer").path("shader").textValue()))
						.isEqualTo(fingerprint.path("shaderSha256").textValue());
			}
		}
	}

	@Test
	void reportCountsUtf8AndOutputPassWithoutEchoingSourceOrRecordMetadata() throws Exception {
		String shader = "// 😀 café";
		JsonNode records = this.mapper.valueToTree(new Object[] {
				Map.of("sceneId", "unicode", "name", "PRIVATE NAME", "sceneData", Map.of(
						"visualizer", Map.of("shader", shader),
						"fx", Map.of("bloom", Map.of("enabled", true), "passes", Map.of("outputPass", true))))
		});
		Map<String, Object> report = SceneSubmissionInventory.inspect(records);
		assertThat(report).containsEntry("validScenes", 1)
				.containsEntry("maxShaderUtf8Bytes", shader.getBytes(StandardCharsets.UTF_8).length)
				.containsEntry("maxEnabledEffectsExcludingOutput", 1);
		assertThat(this.mapper.writeValueAsString(report)).doesNotContain(shader, "PRIVATE NAME");
	}

	@Test
	void auditsBuilderTemplateCustomAndLegacyExportsWithTheirCorrectValidationBoundaries() throws Exception {
		JsonNode records = this.mapper.readTree("""
				[{"sceneId":"builder","sceneData":{"schemaVersion":1,"kind":"builder","builderVersion":1,"objects":[],
				   "settings":{"bloom":{"enabled":true},"tint":{"enabled":true},"effects":{"passes":{"rgbShift":true,"outputPass":true}}}} },
				 {"sceneId":"template","sceneData":{"schemaVersion":1,"kind":"template","templateId":"embedded-scene-0","templateVersion":1}},
				 {"sceneId":"custom","sceneData":{"schemaVersion":1,"kind":"custom","scene":{"visualizer":{"shader":"PRIVATE_SOURCE"}}}},
				 {"sceneId":"legacy","sceneData":{"visualizer":{"shader":"PRIVATE_SOURCE"}}}]
				""");
		JsonNode original = records.deepCopy();
		assertThat(SceneSubmissionInventory.inspect(records)).containsEntry("validScenes", 4).containsEntry("invalidScenes", 0)
				.containsEntry("maxEnabledEffectsExcludingOutput", 3);
		assertThat(records).isEqualTo(original);
		((com.fasterxml.jackson.databind.node.ObjectNode) records.get(0).path("sceneData")).put("builderVersion", 99);
		JsonNode report = this.mapper.valueToTree(SceneSubmissionInventory.inspect(records));
		assertThat(report.path("invalidScenes").asInt()).isEqualTo(1);
		assertThat(report.path("failures").get(0).path("fields").get(0).asText()).isEqualTo("sceneData.builderVersion");
		assertThat(report.toString()).doesNotContain("PRIVATE_SOURCE");
	}

	@Test
	void invalidRecordsHaveBoundedIdentifiersAndFieldDetailsWithoutSource() throws Exception {
		ArrayNode records = this.mapper.createArrayNode();
		for (int index = 0; index < 105; index++) {
			records.addObject().put("sceneId", "id".repeat(100))
					.putObject("sceneData").putObject("visualizer").put("shader", false);
		}
		Map<String, Object> report = SceneSubmissionInventory.inspect(records);
		JsonNode output = this.mapper.valueToTree(report);
		assertThat(report).containsEntry("invalidScenes", 105).containsEntry("omittedFailureDetails", 5);
		assertThat(output.path("failures").size()).isEqualTo(100);
		assertThat(output.path("failures").get(0).path("sceneId").textValue()).hasSize(121);
		assertThat(output.path("failures").get(0).path("fields").toString()).contains("visualizer.shader");
	}

	@Test
	void commandReturnsInvalidStatusAndNeverRewritesInput() throws Exception {
		String marker = "SOURCE_MUST_NOT_APPEAR";
		String content = this.mapper.writeValueAsString(new Object[] { Map.of("id", 22, "sceneData",
				Map.of("visualizer", Map.of("shader", marker.repeat(4000)))) });
		Path input = this.temporaryDirectory.resolve("inventory.json");
		Files.writeString(input, content);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		int status = SceneSubmissionInventory.run(new String[] { input.toString() }, new PrintStream(output));
		assertThat(status).isEqualTo(1);
		assertThat(output.toString(StandardCharsets.UTF_8)).contains("\"sceneId\":\"22\"").doesNotContain(marker);
		assertThat(Files.readString(input)).isEqualTo(content);
	}

	@Test
	void malformedOversizedOrAmbiguousExportsFailWithoutExposingParserInput() throws Exception {
		for (String content : new String[] {
				"SECRET_SOURCE_NOT_JSON", "[] []", "[{\"sceneData\":{},\"sceneData\":{}}]", "[{}]", "{}"
		}) {
			Path input = this.temporaryDirectory.resolve("malformed.json");
			Files.writeString(input, content);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			assertThat(SceneSubmissionInventory.run(new String[] { input.toString() }, new PrintStream(output))).isEqualTo(2);
			assertThat(output.toString(StandardCharsets.UTF_8)).contains("INVALID_INVENTORY_INPUT")
					.doesNotContain("SECRET_SOURCE_NOT_JSON");
		}
		Path large = this.temporaryDirectory.resolve("too-large.json");
		try (RandomAccessFile file = new RandomAccessFile(large.toFile(), "rw")) {
			file.setLength((long) SceneSubmissionInventory.MAX_INPUT_BYTES + 1);
		}
		assertThatThrownBy(() -> SceneSubmissionInventory.readInput(large)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void requiresStrictUtf8InsteadOfAutoDetectingUtf16AndAllowsUtf8Bom() throws Exception {
		Path input = this.temporaryDirectory.resolve("encoding.json");
		for (byte[] bytes : new byte[][] { "[]".getBytes(StandardCharsets.UTF_16LE),
				"[]".getBytes(StandardCharsets.UTF_16BE), new byte[] { '[', '"', (byte) 0xc3, 0x28, '"', ']' } }) {
			Files.write(input, bytes);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			assertThat(SceneSubmissionInventory.run(new String[] { input.toString() }, new PrintStream(output))).isEqualTo(2);
		}
		Files.writeString(input, "\uFEFF[]", StandardCharsets.UTF_8);
		assertThat(SceneSubmissionInventory.readInput(input).isArray()).isTrue();
	}

	@Test
	void untrustedFieldNamesAreNotPrintedAsSource() throws Exception {
		String source = "fetch('PRIVATE_SOURCE')";
		JsonNode records = this.mapper.valueToTree(new Object[] { Map.of("sceneData", Map.of(source, true)) });
		Map<String, Object> report = SceneSubmissionInventory.inspect(records);
		assertThat(this.mapper.writeValueAsString(report)).contains("<invalid field>").doesNotContain(source, "PRIVATE_SOURCE");
	}

	@Test
	void tooManyRecordsAreRejectedBeforeValidation() throws Exception {
		ArrayNode records = this.mapper.createArrayNode();
		for (int index = 0; index <= SceneSubmissionInventory.MAX_SCENES; index++) records.addObject().putObject("sceneData");
		assertThatThrownBy(() -> SceneSubmissionInventory.inspect(records)).isInstanceOf(IllegalArgumentException.class);
	}

	private JsonNode fixture(String filename) throws Exception {
		return this.mapper.readTree(fixtureText(filename));
	}

	private String fixtureText(String filename) throws Exception {
		try (var stream = getClass().getResourceAsStream("/scene-corpus/" + filename)) {
			assertThat(stream).isNotNull();
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String sha256(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}
}
