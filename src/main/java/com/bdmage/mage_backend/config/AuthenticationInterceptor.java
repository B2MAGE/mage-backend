package com.bdmage.mage_backend.config;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

	private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication is required.";
	private static final String BEARER_PREFIX = "Bearer ";

	private final AuthenticationTokenService authenticationTokenService;

	public AuthenticationInterceptor(AuthenticationTokenService authenticationTokenService) {
		this.authenticationTokenService = authenticationTokenService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String token = resolveBearerToken(request);
		User authenticatedUser = this.authenticationTokenService.authenticate(token);
		AuthenticatedUserRequest.authenticate(request, authenticatedUser);
		return true;
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
