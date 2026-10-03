package com.bdmage.mage_backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Explicitly migrates older request fixtures to the custom-v1 transport contract. */
public final class SceneDocumentFixtures {
	private static final ObjectMapper JSON = new ObjectMapper();
	private SceneDocumentFixtures() {}

	public static JsonNode customDocument(JsonNode rawScene) {
		return JSON.createObjectNode().put("schemaVersion", 1).put("kind", "custom").set("scene", rawScene);
	}

	public static JsonNode customDocument(String rawScene) throws Exception {
		return customDocument(JSON.readTree(rawScene));
	}

	public static RequestPostProcessor explicitCustomSceneDocument() {
		return request -> {
			boolean writesDocument = ("POST".equals(request.getMethod()) && "/api/scenes".equals(request.getRequestURI()))
					|| ("PUT".equals(request.getMethod()) && request.getRequestURI().matches("/api/scenes/\\d+"));
			if (!writesDocument || request.getContentAsByteArray() == null || request.getContentAsByteArray().length == 0) {
				return request;
			}
			try {
				JsonNode body = JSON.readTree(request.getContentAsByteArray());
				if (body instanceof ObjectNode object && body.get("sceneData") instanceof ObjectNode scene
						&& !scene.has("schemaVersion") && !scene.has("kind") && !scene.has("templateId")) {
					object.set("sceneData", customDocument(scene));
					request.setContent(JSON.writeValueAsBytes(object));
				}
				return request;
			} catch (Exception ex) {
				throw new AssertionError("Scene request fixture must be valid JSON before envelope conversion", ex);
			}
		};
	}
}
