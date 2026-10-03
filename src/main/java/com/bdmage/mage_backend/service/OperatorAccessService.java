package com.bdmage.mage_backend.service;

import com.bdmage.mage_backend.config.SceneAvailabilityProperties;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.OperatorAccessRequiredException;
import org.springframework.stereotype.Service;

@Service
public class OperatorAccessService {

	private final SceneAvailabilityProperties properties;

	public OperatorAccessService(SceneAvailabilityProperties properties) {
		this.properties = properties;
	}

	public boolean isOperator(Long authenticatedUserId) {
		return authenticatedUserId != null && this.properties.operatorUserIds().contains(authenticatedUserId);
	}

	public void requireOperator(Long authenticatedUserId) {
		if (authenticatedUserId == null) {
			throw new AuthenticationRequiredException("Authentication is required.");
		}
		if (!isOperator(authenticatedUserId)) {
			throw new OperatorAccessRequiredException("Operator access is required.");
		}
	}
}
