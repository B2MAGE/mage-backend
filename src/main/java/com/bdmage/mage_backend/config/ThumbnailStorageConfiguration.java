package com.bdmage.mage_backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ThumbnailStorageProperties.class)
public class ThumbnailStorageConfiguration {
}
