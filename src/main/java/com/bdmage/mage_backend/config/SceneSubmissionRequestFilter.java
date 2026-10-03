package com.bdmage.mage_backend.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import com.bdmage.mage_backend.validation.SceneLimits;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/** Bounds scene submissions before Spring binds the request into maps or DTOs. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SceneSubmissionRequestFilter extends OncePerRequestFilter {

	private static final JsonFactory JSON = JsonFactory.builder()
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.streamReadConstraints(StreamReadConstraints.builder()
					.maxNestingDepth(SceneLimits.SCENE_DEPTH + 1)
					.maxStringLength(SceneLimits.REQUEST_BYTES)
					.maxNumberLength(100)
					.build())
			.build();
	private static final ObjectMapper ERROR_JSON = new ObjectMapper();
	private static final Set<String> CREATE_FIELDS = Set.of("name", "description", "sceneData", "thumbnailObjectKey", "playlistId");
	private static final Set<String> UPDATE_FIELDS = Set.of("name", "description", "sceneData");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// The container's servlet path is decoded and excludes the context path.
		String path = request.getServletPath();
		if (path == null || path.isEmpty()) {
			path = new UrlPathHelper().getPathWithinApplication(request);
		}
		path = new UrlPathHelper().removeSemicolonContent(path);
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return !("POST".equals(request.getMethod()) && "/api/scenes".equals(path))
				&& !("PUT".equals(request.getMethod()) && path.matches("/api/scenes/[^/]+"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		byte[] body;
		try {
			String coding = contentCoding(request);
			validateCharset(request);
			byte[] wireBody = readBounded(request.getInputStream());
			if ("gzip".equals(coding)) {
				try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(wireBody))) {
					body = readBounded(gzip);
				}
			} else {
				body = wireBody;
			}
			validateJson(body, "POST".equals(request.getMethod()) ? CREATE_FIELDS : UPDATE_FIELDS);
		} catch (SubmissionRejected ex) {
			writeError(request, response, ex.status, ex.code, ex.getMessage(), ex.details);
			return;
		} catch (StreamConstraintsException ex) {
			writeError(request, response, 400, "VALIDATION_ERROR", "Request validation failed.",
					Map.of("sceneData", "JSON nesting or scalar size exceeds the submission limits."));
			return;
		} catch (IOException | IllegalArgumentException ex) {
			// Never expose parser excerpts: they can contain submitted source or other private fields.
			writeError(request, response, 400, "MALFORMED_REQUEST", "Request body could not be parsed.", Map.of());
			return;
		}
		chain.doFilter(new CachedRequest(request, body), response);
	}

	private static String contentCoding(HttpServletRequest request) throws SubmissionRejected {
		List<String> headers = Collections.list(request.getHeaders("Content-Encoding"));
		if (headers.isEmpty()) {
			return "identity";
		}
		String coding = headers.size() == 1 ? headers.getFirst().trim() : "";
		if ("identity".equalsIgnoreCase(coding) || "gzip".equalsIgnoreCase(coding)) {
			return coding.toLowerCase(java.util.Locale.ROOT);
		}
		throw new SubmissionRejected(415, "UNSUPPORTED_MEDIA_TYPE", "Unsupported request content encoding.",
				Map.of("contentEncoding", "Use identity or a single gzip encoding."));
	}

	private static void validateCharset(HttpServletRequest request) throws SubmissionRejected {
		String contentType = request.getContentType();
		if (contentType != null) {
			MediaType mediaType = MediaType.parseMediaType(contentType);
			if (mediaType.getCharset() != null && !StandardCharsets.UTF_8.equals(mediaType.getCharset())) {
				throw new SubmissionRejected(415, "UNSUPPORTED_MEDIA_TYPE", "Scene submissions must use UTF-8.",
						Map.of("contentType", "Use UTF-8 JSON."));
			}
		}
	}

	private static byte[] readBounded(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
		byte[] buffer = new byte[8192];
		int count;
		// Read at most one byte beyond the limit; never trust Content-Length for allocation or enforcement.
		while ((count = input.read(buffer, 0, Math.min(buffer.length, SceneLimits.REQUEST_BYTES + 1 - output.size()))) != -1) {
			if (output.size() + count > SceneLimits.REQUEST_BYTES) {
				throw new SubmissionRejected(413, "REQUEST_TOO_LARGE", "Scene submission exceeds the request size limit.",
						Map.of("request", "Must be at most " + SceneLimits.REQUEST_BYTES + " bytes, both encoded and decoded."));
			}
			output.write(buffer, 0, count);
		}
		return output.toByteArray();
	}

	private static void validateJson(byte[] body, Set<String> allowedFields) throws IOException {
		int offset = body.length >= 3 && body[0] == (byte) 0xef && body[1] == (byte) 0xbb && body[2] == (byte) 0xbf ? 3 : 0;
		// A Reader prevents Jackson's byte parser from auto-detecting UTF-16/32 as an alternate encoding.
		var decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		try (JsonParser parser = JSON.createParser(new InputStreamReader(
				new ByteArrayInputStream(body, offset, body.length - offset), decoder))) {
			if (parser.nextToken() != JsonToken.START_OBJECT) {
				throw new IOException("Expected request object");
			}
			readValue(parser, 0, true, new KeyCount(allowedFields));
			if (parser.nextToken() != null) {
				throw new IOException("Trailing JSON value");
			}
		}
	}

	private static void readValue(JsonParser parser, int sceneDepth, boolean requestRoot, KeyCount counts)
			throws IOException {
		JsonToken token = parser.currentToken();
		if (sceneDepth > 0 && ++counts.sceneNodes > SceneLimits.TOTAL_NODES) {
			throw invalid("sceneData", "Must have at most " + SceneLimits.TOTAL_NODES + " JSON values.");
		}
		if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
			if (sceneDepth > SceneLimits.SCENE_DEPTH) {
				throw invalid("sceneData", "Must have at most " + SceneLimits.SCENE_DEPTH + " nested containers.");
			}
		}
		if (token == JsonToken.START_OBJECT) {
			int objectKeys = 0;
			while (parser.nextToken() != JsonToken.END_OBJECT) {
				if (parser.currentToken() != JsonToken.FIELD_NAME) {
					throw new IOException("Expected field");
				}
				if (requestRoot && !counts.allowedFields.contains(parser.currentName())) {
					String field = parser.currentName();
					String safeField = field.length() <= 64 && field.matches("[A-Za-z][A-Za-z0-9_]*") ? field : "request";
					throw invalid(safeField, "This field is not allowed in a scene submission.");
				}
				if (++objectKeys > SceneLimits.OBJECT_KEYS) {
					throw invalid(sceneDepth > 0 ? "sceneData" : "request", "Objects must have at most " + SceneLimits.OBJECT_KEYS + " fields.");
				}
				if (sceneDepth > 0 && parser.currentName().getBytes(StandardCharsets.UTF_8).length > SceneLimits.KEY_BYTES) {
					throw invalid("sceneData", "Field names must be at most " + SceneLimits.KEY_BYTES + " UTF-8 bytes.");
				}
				if (++counts.total > SceneLimits.TOTAL_KEYS + SceneLimits.OBJECT_KEYS) {
					throw invalid("request", "Too many JSON fields.");
				}
				if (sceneDepth > 0 && ++counts.scene > SceneLimits.TOTAL_KEYS) {
					throw invalid("sceneData", "Must have at most " + SceneLimits.TOTAL_KEYS + " total fields.");
				}
				boolean startsScene = requestRoot && "sceneData".equals(parser.currentName());
				if (parser.nextToken() == null) {
					throw new IOException("Missing field value");
				}
				readValue(parser, startsScene ? 1 : (sceneDepth > 0 ? sceneDepth + 1 : 0), false, counts);
			}
		} else if (token == JsonToken.START_ARRAY) {
			int arrayItems = 0;
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				if (parser.currentToken() == null) {
					throw new IOException("Unterminated array");
				}
				if (++arrayItems > SceneLimits.ARRAY_ITEMS) {
					throw invalid(sceneDepth > 0 ? "sceneData" : "request", "Arrays must have at most " + SceneLimits.ARRAY_ITEMS + " items.");
				}
				readValue(parser, sceneDepth > 0 ? sceneDepth + 1 : 0, false, counts);
			}
		} else if (token == null || !token.isScalarValue()) {
			throw new IOException("Expected JSON value");
		}
	}

	private static SubmissionRejected invalid(String field, String message) {
		return new SubmissionRejected(400, "VALIDATION_ERROR", "Request validation failed.", Map.of(field, message));
	}

	private static void writeError(HttpServletRequest request, HttpServletResponse response, int status,
			String code, String message, Map<String, String> details) throws IOException {
		Map<String, Object> error = new LinkedHashMap<>();
		error.put("code", code);
		error.put("message", message);
		if (!details.isEmpty()) {
			error.put("details", details);
		}
		error.put("path", request.getRequestURI());
		error.put("timestamp", Instant.now().toString());
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		ERROR_JSON.writeValue(response.getOutputStream(), error);
	}

	private static final class KeyCount {
		final Set<String> allowedFields;
		int total;
		int scene;
		int sceneNodes;

		KeyCount(Set<String> allowedFields) {
			this.allowedFields = allowedFields;
		}
	}

	private static final class SubmissionRejected extends IOException {
		final int status;
		final String code;
		final Map<String, String> details;

		SubmissionRejected(int status, String code, String message, Map<String, String> details) {
			super(message);
			this.status = status;
			this.code = code;
			this.details = details;
		}
	}

	private static final class CachedRequest extends HttpServletRequestWrapper {
		private final byte[] body;

		CachedRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override public int getContentLength() { return this.body.length; }
		@Override public long getContentLengthLong() { return this.body.length; }
		@Override public String getCharacterEncoding() { return StandardCharsets.UTF_8.name(); }

		@Override
		public String getHeader(String name) {
			if ("Content-Encoding".equalsIgnoreCase(name) || "Transfer-Encoding".equalsIgnoreCase(name)) {
				return null;
			}
			return "Content-Length".equalsIgnoreCase(name) ? Integer.toString(this.body.length) : super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			if ("Content-Encoding".equalsIgnoreCase(name) || "Transfer-Encoding".equalsIgnoreCase(name)) {
				return Collections.emptyEnumeration();
			}
			return "Content-Length".equalsIgnoreCase(name)
					? Collections.enumeration(List.of(Integer.toString(this.body.length))) : super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			List<String> names = new java.util.ArrayList<>(Collections.list(super.getHeaderNames()));
			names.removeIf(name -> "Content-Encoding".equalsIgnoreCase(name) || "Transfer-Encoding".equalsIgnoreCase(name));
			if (names.stream().noneMatch("Content-Length"::equalsIgnoreCase)) {
				names.add("Content-Length");
			}
			return Collections.enumeration(names);
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream input = new ByteArrayInputStream(this.body);
			return new ServletInputStream() {
				@Override public int read() { return input.read(); }
				@Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
				@Override public boolean isFinished() { return input.available() == 0; }
				@Override public boolean isReady() { return true; }
				@Override public void setReadListener(ReadListener listener) {
					throw new IllegalStateException("Scene submission binding uses blocking reads.");
				}
			};
		}

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}
	}
}
