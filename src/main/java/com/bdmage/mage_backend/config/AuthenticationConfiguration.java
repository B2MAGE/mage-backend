package com.bdmage.mage_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AuthenticationConfiguration implements WebMvcConfigurer {

	private final AuthenticationInterceptor authenticationInterceptor;

	public AuthenticationConfiguration(AuthenticationInterceptor authenticationInterceptor) {
		this.authenticationInterceptor = authenticationInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this.authenticationInterceptor)
				.addPathPatterns("/users/**");
	}
}
