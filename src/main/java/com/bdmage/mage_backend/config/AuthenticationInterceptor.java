package com.bdmage.mage_backend.config;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String PUBLIC_PRESET_DETAIL_PATTERN = "/api/presets/{id}";
	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

	private final AuthenticationTokenService authenticationTokenService;

	public AuthenticationInterceptor(AuthenticationTokenService authenticationTokenService) {
		this.authenticationTokenService = authenticationTokenService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// Public preset detail pages allow anonymous GET /api/presets/{id} reads without
		// opening write or owner-sensitive preset routes.
		if (isPublicPresetDetailRequest(request)) {
			return true;
		}

		String token = resolveBearerToken(request);
		User authenticatedUser = this.authenticationTokenService.authenticate(token);
		AuthenticatedUserRequest.authenticate(request, authenticatedUser);
		return true;
	}

	private static boolean isPublicPresetDetailRequest(HttpServletRequest request) {
		return HttpMethod.GET.matches(request.getMethod())
				&& PATH_MATCHER.match(PUBLIC_PRESET_DETAIL_PATTERN, pathWithinApplication(request));
	}

	private static String pathWithinApplication(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		String contextPath = request.getContextPath();

		if (StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)) {
			return requestUri.substring(contextPath.length());
		}

		return requestUri;
	}

	private static String resolveBearerToken(HttpServletRequest request) {
		String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith(BEARER_PREFIX)) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}

		String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
		if (!StringUtils.hasText(token)) {
			throw new AuthenticationRequiredException(AUTHENTICATION_REQUIRED_MESSAGE);
		}

		return token;
	}
}
