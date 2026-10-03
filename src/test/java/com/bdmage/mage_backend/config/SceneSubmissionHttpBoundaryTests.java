package com.bdmage.mage_backend.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.bdmage.mage_backend.validation.SceneLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Real HTTP framing and Spring binding, with no database or authentication mocks involved. */
@SpringBootTest(classes = SceneSubmissionHttpBoundaryTests.TestApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"spring.config.location=optional:classpath:/submission-filter-test-empty.properties",
				"server.servlet.context-path=/test", "spring.main.banner-mode=off"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SceneSubmissionHttpBoundaryTests {
	@LocalServerPort private int port;
	@Autowired private BindingProbe probe;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	@BeforeEach
	void reset() { probe.calls.set(0); }

	@Test
	void chunkedRequestWithNoContentLengthIsBoundedBeforeSpringBinding() throws Exception {
		byte[] body = paddedRequest(SceneLimits.REQUEST_BYTES + 1);
		var publisher = HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body));
		assertEquals(-1, publisher.contentLength());
		HttpResponse<String> response = send("/api/scenes", "POST", publisher, null);
		assertEquals(413, response.statusCode(), response.body());
		assertTrue(response.body().contains("REQUEST_TOO_LARGE"));
		assertEquals(0, probe.calls.get());
	}

	@Test
	void exactByteLimitWithChunkedTransferReachesRealMapBinding() throws Exception {
		byte[] body = paddedRequest(SceneLimits.REQUEST_BYTES);
		HttpResponse<String> response = send("/api/scenes", "POST",
				HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)), null);
		assertEquals(204, response.statusCode(), response.body());
		assertEquals(1, probe.calls.get());
	}

	@Test
	void acceptsGzipAtRealControllerAndRejectsExpandedOverflow() throws Exception {
		byte[] valid = SceneSubmissionRequestFilterTests.gzip("{\"sceneData\":{},\"name\":\"é\"}".getBytes(StandardCharsets.UTF_8));
		assertEquals(204, send("/api/scenes/17", "PUT", HttpRequest.BodyPublishers.ofByteArray(valid), "gzip").statusCode());
		assertEquals(1, probe.calls.get());
		byte[] oversized = SceneSubmissionRequestFilterTests.gzip(paddedRequest(SceneLimits.REQUEST_BYTES + 1));
		HttpResponse<String> rejected = send("/api/scenes/17", "PUT",
				HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(oversized)), "gzip");
		assertEquals(413, rejected.statusCode(), rejected.body());
		assertEquals(1, probe.calls.get());
	}

	@Test
	void encodedAndMatrixParameterPathsCannotBypassRequestGuard() throws Exception {
		for (String path : List.of("/api/%73cenes", "/api/scenes;test=1", "/api;test=1/scenes")) {
			HttpResponse<String> response = send(path, "POST", HttpRequest.BodyPublishers.ofByteArray(paddedRequest(SceneLimits.REQUEST_BYTES + 1)), null);
			assertEquals(413, response.statusCode(), path + " " + response.body());
		}
		HttpResponse<String> response = send("/api/scenes;test=1/17;foo=bar", "PUT",
				HttpRequest.BodyPublishers.ofByteArray(paddedRequest(SceneLimits.REQUEST_BYTES + 1)), null);
		assertEquals(413, response.statusCode(), response.body());
		assertEquals(0, probe.calls.get());
	}

	@Test
	void duplicateKeysAndExcessiveDepthNeverReachMapBinding() throws Exception {
		String deep = "{\"sceneData\":" + "[".repeat(SceneLimits.SCENE_DEPTH + 1) + "0" + "]".repeat(SceneLimits.SCENE_DEPTH + 1) + "}";
		for (String body : List.of("{\"sceneData\":{\"secret\":1,\"secret\":2}}", deep)) {
			HttpResponse<String> response = send("/api/scenes", "POST", HttpRequest.BodyPublishers.ofString(body), null);
			assertEquals(400, response.statusCode(), response.body());
			assertFalse(response.body().contains("secret"));
		}
		assertEquals(0, probe.calls.get());
	}

	private HttpResponse<String> send(String path, String method, HttpRequest.BodyPublisher body, String coding) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/test" + path))
				.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json").method(method, body);
		if (coding != null) request.header("Content-Encoding", coding);
		return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static byte[] paddedRequest(int bytes) {
		String json = "{\"sceneData\":{},\"name\":\"test\"}";
		return (json + " ".repeat(bytes - json.length())).getBytes(StandardCharsets.UTF_8);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
	@Import({SceneSubmissionRequestFilter.class, BindingProbe.class})
	static class TestApplication {}

	@RestController
	static class BindingProbe {
		final AtomicInteger calls = new AtomicInteger();

		@RequestMapping(path = {"/api/scenes", "/api/scenes/{id}"}, method = {RequestMethod.POST, RequestMethod.PUT})
		@ResponseStatus(HttpStatus.NO_CONTENT)
		void accept(@RequestBody Map<String, Object> body) {
			assertInstanceOf(Map.class, body.get("sceneData"));
			calls.incrementAndGet();
		}
	}
}
