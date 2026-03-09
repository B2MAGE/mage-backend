package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.dto.HealthResponse;
import com.bdmage.mage_backend.dto.ReadinessResponse;
import com.bdmage.mage_backend.service.ReadinessService;
import com.bdmage.mage_backend.service.ReadinessService.ReadinessStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	private final ReadinessService readinessService;

	public HealthController(ReadinessService readinessService) {
		this.readinessService = readinessService;
	}

	@GetMapping("/health")
	ResponseEntity<HealthResponse> health() {
		return ResponseEntity.ok(new HealthResponse("UP"));
	}

	@GetMapping("/ready")
	ResponseEntity<ReadinessResponse> ready() {
		ReadinessStatus readiness = this.readinessService.checkReadiness();
		HttpStatus status = readiness.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
		String applicationStatus = readiness.ready() ? "UP" : "DOWN";

		return ResponseEntity.status(status)
			.body(new ReadinessResponse(applicationStatus, readiness.database()));
	}
}
