package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.dto.HealthResponse;
import com.bdmage.mage_backend.dto.ReadinessResponse;
import com.bdmage.mage_backend.service.ReadinessService;
import com.bdmage.mage_backend.service.ReadinessService.ReadinessStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HealthController
 *
 * In a backend API, a *controller* is responsible for handling incoming HTTP
 * requests.
 *
 * The @RestController annotation tells Spring Boot:
 * "This class contains endpoints that should respond to web requests."
 *
 * Each method in this class is mapped to a URL using annotations
 * like @GetMapping.
 *
 * These endpoints are commonly used by monitoring tools, load balancers,
 * or container platforms (like Docker or Kubernetes) to determine whether
 * the application is healthy and ready to accept traffic.
 */
@RestController
public class HealthController {

	/**
	 * The ReadinessService performs deeper checks to determine if the
	 * application is actually ready to serve requests.
	 *
	 * Spring automatically injects this service through the constructor.
	 * This is called *dependency injection*.
	 */
	private final ReadinessService readinessService;

	/**
	 * Constructor used by Spring to provide the ReadinessService dependency.
	 */
	public HealthController(ReadinessService readinessService) {
		this.readinessService = readinessService;
	}

	/**
	 * GET /health
	 *
	 * This endpoint is called a *liveness check*.
	 *
	 * Its only purpose is to confirm that the server process is still running.
	 * If this method executes successfully, the application is considered "alive".
	 *
	 * Example request:
	 * GET http://localhost:8080/health
	 *
	 * Example response:
	 * { "status": "UP" }
	 */
	@GetMapping("/health")
	ResponseEntity<HealthResponse> health() {

		// ResponseEntity allows us to control both the HTTP status code
		// and the JSON response body returned to the client.
		return ResponseEntity.ok(new HealthResponse("UP"));
	}

	/**
	 * GET /ready
	 *
	 * This endpoint is called a *readiness check*.
	 *
	 * Unlike the liveness check, readiness verifies that the application
	 * can actually serve requests correctly.
	 *
	 * For example, the application might be running but unable to serve
	 * requests if:
	 * - the database is unavailable
	 * - external services are down
	 *
	 * In this project, readiness checks whether the database connection works.
	 */
	@GetMapping("/ready")
	ResponseEntity<ReadinessResponse> ready() {

		// Ask the readiness service to perform system checks
		ReadinessStatus readiness = this.readinessService.checkReadiness();

		/**
		 * HTTP status codes communicate the state of the server.
		 *
		 * 200 OK
		 * The application is ready to handle requests.
		 *
		 * 503 SERVICE_UNAVAILABLE
		 * The server is running but not ready to serve traffic yet.
		 */
		HttpStatus status = readiness.ready()
				? HttpStatus.OK
				: HttpStatus.SERVICE_UNAVAILABLE;

		// Human-readable status returned in the JSON response
		String applicationStatus = readiness.ready() ? "UP" : "DOWN";

		return ResponseEntity.status(status)
				.body(new ReadinessResponse(applicationStatus, readiness.database()));
	}
}