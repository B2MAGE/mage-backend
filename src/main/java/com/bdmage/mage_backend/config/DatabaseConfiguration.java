package com.bdmage.mage_backend.config;

import java.sql.SQLException;
import java.time.Duration;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfiguration {

	@Bean
	DataSource dataSource(DatabaseProperties properties) {
		properties.validate();

		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(properties.url());
		config.setUsername(properties.username());
		config.setPassword(properties.password());
		config.setPoolName("mage-backend");
		config.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
		config.setValidationTimeout(Duration.ofSeconds(5).toMillis());
		config.setInitializationFailTimeout(Duration.ofSeconds(10).toMillis());

		HikariDataSource dataSource = new HikariDataSource(config);
		try {
			dataSource.getConnection().close();
			return dataSource;
		}
		catch (SQLException ex) {
			dataSource.close();
			throw new ApplicationContextException(
				"Unable to connect to the configured PostgreSQL database. Verify "
					+ "SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, and "
					+ "SPRING_DATASOURCE_PASSWORD.",
				ex
			);
		}
	}
}
