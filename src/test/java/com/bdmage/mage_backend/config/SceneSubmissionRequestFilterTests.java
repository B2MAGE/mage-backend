package com.bdmage.mage_backend.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;

import com.bdmage.mage_backend.validation.SceneLimits;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SceneSubmissionRequestFilterTests {
	private final SceneSubmissionRequestFilter filter = new SceneSubmissionRequestFilter();

	@Test
	void acceptsExactByteLimitAndPreservesUtf8Body() throws Exception {
		String json = "{\"sceneData\":{},\"name\":\"é\"}";
		byte[] input = (json + " ".repeat(SceneLimits.REQUEST_BYTES - json.getBytes(StandardCharsets.UTF_8).length))
				.getBytes(StandardCharsets.UTF_8);
		MockHttpServletRequest request = request(input);
		AtomicReference<byte[]> actual = new AtomicReference<>();
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (cached, ignored) -> actual.set(cached.getInputStream().readAllBytes()));
		assertEquals(200, response.getStatus());
		assertArrayEquals(input, actual.get());
	}

	@ParameterizedTest
	@ValueSource(longs = {-1, 0, 1, 99999999})
	void rejectsActualBytesRegardlessOfClaimedLength(long declaredLength) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/scenes") {
			@Override public long getContentLengthLong() { return declaredLength; }
			@Override public int getContentLength() { return (int) declaredLength; }
		};
		request.setContent(new byte[SceneLimits.REQUEST_BYTES + 1]);
		request.addHeader("Content-Length", Long.toString(declaredLength));
		assertRejected(request, 413, "REQUEST_TOO_LARGE");
	}

	@Test
	void acceptsGzipAndPresentsExpandedLengthAndHeadersToBinder() throws Exception {
		byte[] original = "{\"sceneData\":{},\"name\":\"é\"}".getBytes(StandardCharsets.UTF_8);
		MockHttpServletRequest request = request(gzip(original));
		request.addHeader("Content-Encoding", "gzip");
		request.addHeader("Transfer-Encoding", "chunked");
		AtomicReference<HttpServletRequest> actual = new AtomicReference<>();
		filter.doFilter(request, new MockHttpServletResponse(), (cached, ignored) -> actual.set((HttpServletRequest) cached));
		assertNotNull(actual.get());
		assertArrayEquals(original, actual.get().getInputStream().readAllBytes());
		assertEquals(original.length, actual.get().getContentLength());
		assertEquals(Integer.toString(original.length), actual.get().getHeader("Content-Length"));
		assertNull(actual.get().getHeader("Content-Encoding"));
		assertNull(actual.get().getHeader("Transfer-Encoding"));
		assertFalse(actual.get().getHeaders("Content-Encoding").hasMoreElements());
		assertFalse(Collections.list(actual.get().getHeaderNames()).contains("Content-Encoding"));
		assertEquals("UTF-8", actual.get().getCharacterEncoding());
	}

	@Test
	void rejectsSmallGzipThatExpandsBeyondLimit() throws Exception {
		MockHttpServletRequest request = request(gzip(" ".repeat(SceneLimits.REQUEST_BYTES + 1).getBytes(StandardCharsets.UTF_8)));
		request.addHeader("Content-Encoding", "gzip");
		assertTrue(request.getContentLength() < 1024);
		assertRejected(request, 413, "REQUEST_TOO_LARGE");
	}

	@Test
	void rejectsOversizeWireBodyEvenIfGzipCouldBeSmallWhenDecoded() throws Exception {
		MockHttpServletRequest request = request(new byte[SceneLimits.REQUEST_BYTES + 1]);
		request.addHeader("Content-Encoding", "gzip");
		assertRejected(request, 413, "REQUEST_TOO_LARGE");
	}

	@ParameterizedTest
	@ValueSource(strings = {"br", "gzip, gzip", "gzip, identity", ""})
	void rejectsUnsupportedContentCoding(String coding) throws Exception {
		MockHttpServletRequest request = request("{}");
		request.addHeader("Content-Encoding", coding);
		assertRejected(request, 415, "UNSUPPORTED_MEDIA_TYPE");
	}

	@Test
	void rejectsRepeatedEncodingHeaders() throws Exception {
		MockHttpServletRequest request = request("{}");
		request.addHeader("Content-Encoding", "gzip");
		request.addHeader("Content-Encoding", "identity");
		assertRejected(request, 415, "UNSUPPORTED_MEDIA_TYPE");
	}

	@Test
	void rejectsInvalidGzipWithoutEchoingInput() throws Exception {
		MockHttpServletRequest request = request("secret submitted source");
		request.addHeader("Content-Encoding", "gzip");
		MockHttpServletResponse response = assertRejected(request, 400, "MALFORMED_REQUEST");
		assertFalse(response.getContentAsString().contains("secret"));
	}

	@Test
	void rejectsNonUtf8AndMalformedUtf8() throws Exception {
		MockHttpServletRequest request = request("{}");
		request.setContentType("application/json;charset=UTF-16");
		assertRejected(request, 415, "UNSUPPORTED_MEDIA_TYPE");
		assertRejected(request(new byte[] {'{', '"', 'x', '"', ':', '"', (byte) 0xc3, '"', '}'}), 400, "MALFORMED_REQUEST");
		assertRejected(request("{\"sceneData\":{}}".getBytes(StandardCharsets.UTF_16LE)), 400, "MALFORMED_REQUEST");
		assertAccepted(request("\ufeff{\"sceneData\":{}}"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "[]", "{}{}", "{\"sceneData\":{\"x\":1,\"x\":2}}", "{\"sceneData\":{},\"sceneData\":{}}", "{\"sceneData\":{\"x\":NaN}}", "{\"sceneData\":{\"x\":Infinity}}"})
	void rejectsMalformedAndAmbiguousJsonBeforeBinding(String input) throws Exception {
		assertRejected(request(input), 400, "MALFORMED_REQUEST");
	}

	@Test
	void enforcesSceneDepthBeforeBinding() throws Exception {
		assertAccepted(request("{\"sceneData\":" + nestedObjects(SceneLimits.SCENE_DEPTH) + "}"));
		assertRejected(request("{\"sceneData\":" + nestedObjects(SceneLimits.SCENE_DEPTH + 1) + "}"), 400, "VALIDATION_ERROR");
	}

	@Test
	void enforcesArrayLengthBeforeBinding() throws Exception {
		String values = "0,".repeat(SceneLimits.ARRAY_ITEMS - 1) + "0";
		assertAccepted(request("{\"sceneData\":{\"values\":[" + values + "]}}"));
		assertRejected(request("{\"sceneData\":{\"values\":[" + values + ",0]}}"), 400, "VALIDATION_ERROR");
	}

	@Test
	void enforcesObjectFieldAndTotalFieldLimitsBeforeBinding() throws Exception {
		String fields = fields(SceneLimits.OBJECT_KEYS);
		assertAccepted(request("{\"sceneData\":{" + fields + "}}"));
		assertRejected(request("{\"sceneData\":{" + fields + ",\"extra\":0}}"), 400, "VALIDATION_ERROR");
		String manyObjects = IntStream.range(0, 8).mapToObj(i -> "\"g" + i + "\":{" + fields + "}").collect(Collectors.joining(","));
		assertRejected(request("{\"sceneData\":{" + manyObjects + "}}"), 400, "VALIDATION_ERROR");
	}

	@Test
	void boundsUtf8FieldNamesWithoutEchoingThem() throws Exception {
		String key = "é".repeat(SceneLimits.KEY_BYTES / 2);
		assertAccepted(request("{\"sceneData\":{\"" + key + "\":0}}"));
		MockHttpServletResponse response = assertRejected(request("{\"sceneData\":{\"" + key + "é\":0}}"), 400, "VALIDATION_ERROR");
		assertFalse(response.getContentAsString().contains(key));
	}

	@Test
	void boundsTotalValuesInArraysBeforeBinding() throws Exception {
		String array = "[" + "0,".repeat(SceneLimits.ARRAY_ITEMS - 1) + "0]";
		String oversized = "{\"sceneData\":[" + (array + ",").repeat(SceneLimits.ARRAY_ITEMS - 1) + array + "]}";
		assertRejected(request(oversized), 400, "VALIDATION_ERROR");
	}

	@Test
	void guardsCreateAndUpdatePathsIncludingContextAndDecodedPaths() throws Exception {
		for (String path : List.of("/api/scenes", "/api/scenes/", "/api/%73cenes", "/app/api/scenes")) {
			MockHttpServletRequest request = request(new byte[SceneLimits.REQUEST_BYTES + 1]);
			request.setRequestURI(path);
			if (path.startsWith("/app")) request.setContextPath("/app");
			assertRejected(request, 413, "REQUEST_TOO_LARGE");
		}
		for (String path : List.of("/api/scenes/12", "/api/scenes/invalid-id", "/api/scenes/12/")) {
			MockHttpServletRequest request = request(new byte[SceneLimits.REQUEST_BYTES + 1]);
			request.setMethod("PUT");
			request.setRequestURI(path);
			assertRejected(request, 413, "REQUEST_TOO_LARGE");
		}
	}

	@Test
	void doesNotReadBodiesForUnrelatedEndpoints() throws Exception {
		for (String path : List.of("/api/scenes/thumbnail/presign", "/api/scenes/12/tags", "/api/auth/login")) {
			MockHttpServletRequest request = request(new byte[SceneLimits.REQUEST_BYTES + 1]);
			request.setRequestURI(path);
			assertAccepted(request);
		}
		MockHttpServletRequest request = request("not JSON");
		request.setMethod("GET");
		assertAccepted(request);
	}

	private void assertAccepted(MockHttpServletRequest request) throws Exception {
		AtomicBoolean called = new AtomicBoolean();
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (ignored, ignoredResponse) -> called.set(true));
		assertTrue(called.get(), response.getContentAsString());
	}

	private MockHttpServletResponse assertRejected(MockHttpServletRequest request, int status, String code) throws Exception {
		AtomicBoolean called = new AtomicBoolean();
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (ignored, ignoredResponse) -> called.set(true));
		assertFalse(called.get(), "Rejected submissions must not reach binding or application code");
		assertEquals(status, response.getStatus(), response.getContentAsString());
		var json = new ObjectMapper().readTree(response.getContentAsByteArray());
		assertEquals(code, json.get("code").asText());
		assertTrue(json.has("path"));
		assertTrue(json.has("timestamp"));
		return response;
	}

	private static MockHttpServletRequest request(String body) { return request(body.getBytes(StandardCharsets.UTF_8)); }
	private static MockHttpServletRequest request(byte[] body) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/scenes");
		request.setContentType("application/json");
		request.setContent(body);
		return request;
	}

	static byte[] gzip(byte[] body) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(body); }
		return bytes.toByteArray();
	}

	private static String nestedObjects(int depth) { return "{\"x\":".repeat(depth - 1) + "{}" + "}".repeat(depth - 1); }
	private static String fields(int count) {
		return IntStream.range(0, count).mapToObj(i -> "\"k" + i + "\":0").collect(Collectors.joining(","));
	}
}
