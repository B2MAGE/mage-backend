package com.bdmage.mage_backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/** Scene source and availability must be fetched again after an operator changes access. */
public class SceneAvailabilityCacheInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		return true;
	}
}
