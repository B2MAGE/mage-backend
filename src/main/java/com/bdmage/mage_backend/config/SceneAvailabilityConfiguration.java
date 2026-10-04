package com.bdmage.mage_backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SceneAvailabilityProperties.class, AdministratorProperties.class})
public class SceneAvailabilityConfiguration implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new SceneAvailabilityCacheInterceptor())
				.addPathPatterns("/api/scenes/**", "/api/profiles/**", "/api/users/*/scenes",
						"/api/scene-availability/**", "/api/rendering-status", "/api/admin/**")
				.order(-100);
	}
}
