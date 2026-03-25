package com.bdmage.mage_backend.config;

import com.bdmage.mage_backend.model.User;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthenticatedUserRequest {

	public static final String USER_ATTRIBUTE = "authenticatedUser";
	public static final String USER_ID_ATTRIBUTE = "authenticatedUserId";

	private AuthenticatedUserRequest() {
	}

	public static void authenticate(HttpServletRequest request, User user) {
		request.setAttribute(USER_ATTRIBUTE, user);
		request.setAttribute(USER_ID_ATTRIBUTE, user.getId());
	}
}
