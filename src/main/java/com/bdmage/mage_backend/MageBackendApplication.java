package com.bdmage.mage_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MageBackendApplication
 *
 * This class is the main entry point of the backend application.
 *
 * When we run this class, Spring Boot starts the entire system:
 *
 * 1. It scans the project for components like:
 * - controllers
 * - services
 * - configuration classes
 *
 * 2. It loads application configuration (properties, environment variables,
 * etc.)
 *
 * 3. It starts the embedded web server (Tomcat) so the API can receive HTTP
 * requests.
 *
 * The @SpringBootApplication annotation is a convenience annotation that
 * combines several important Spring annotations:
 *
 * - @Configuration → allows the class to define configuration
 * - @EnableAutoConfiguration → lets Spring automatically configure many
 * components
 * - @ComponentScan → tells Spring where to find controllers, services, etc.
 *
 * Because this class sits at the root package (com.bdmage.mage_backend),
 * Spring will automatically scan all subpackages for components.
 */
@SpringBootApplication
public class MageBackendApplication {

	/**
	 * Standard Java entry point.
	 *
	 * When this method runs, Spring Boot initializes the application
	 * and starts the embedded web server.
	 *
	 * After startup, the backend begins listening for HTTP requests
	 * such as:
	 *
	 * GET /health
	 * GET /ready
	 */
	public static void main(String[] args) {

		// Bootstraps the Spring application and starts the server
		SpringApplication.run(MageBackendApplication.class, args);
	}
}