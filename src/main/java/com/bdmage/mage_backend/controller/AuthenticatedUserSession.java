package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.model.User;
import jakarta.servlet.http.HttpSession;

final class AuthenticatedUserSession {

	static final String USER_ID_ATTRIBUTE = "authenticatedUserId";

	private AuthenticatedUserSession() {
	}

	static void authenticate(HttpSession session, User user) {
		session.setAttribute(USER_ID_ATTRIBUTE, user.getId());
	}
}
