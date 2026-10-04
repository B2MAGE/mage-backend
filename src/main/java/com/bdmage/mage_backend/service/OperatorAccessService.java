package com.bdmage.mage_backend.service;

import com.bdmage.mage_backend.config.AdministratorProperties;
import com.bdmage.mage_backend.dto.ModerationCapabilities;
import com.bdmage.mage_backend.repository.ModeratorPermissionRepository;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.OperatorAccessRequiredException;
import org.springframework.stereotype.Service;

@Service
public class OperatorAccessService {

	private final AdministratorProperties administrators;
	private final ModeratorPermissionRepository permissions;

	public OperatorAccessService(AdministratorProperties administrators, ModeratorPermissionRepository permissions) {
		this.administrators = administrators;
		this.permissions = permissions;
	}

	public boolean isOperator(Long authenticatedUserId) {
		return authenticatedUserId != null && (isAdministrator(authenticatedUserId) || this.permissions.isModerator(authenticatedUserId));
	}

	public boolean isAdministrator(Long id) { return id != null && this.administrators.userIds().contains(id); }

	public void requireAdministrator(Long id) {
		if (id == null) throw new AuthenticationRequiredException("Authentication is required.");
		if (!isAdministrator(id)) throw new OperatorAccessRequiredException("Administrator access is required.");
	}

	public ModerationCapabilities capabilities(Long id) {
		if (id == null) throw new AuthenticationRequiredException("Authentication is required.");
		return new ModerationCapabilities(isOperator(id), isAdministrator(id), isAdministrator(id));
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
