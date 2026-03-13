package com.bdmage.mage_backend.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoogleAuthProperties.class)
public class GoogleAuthConfiguration {

	@Bean
	GoogleIdTokenVerifier googleIdTokenVerifier(GoogleAuthProperties properties) {
		properties.validate();

		return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
				.setAudience(properties.allowedClientIds())
				.build();
	}
}
