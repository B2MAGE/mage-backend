package com.bdmage.mage_backend.config;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthenticationInterceptorTests {

	@Test
	void preHandleAuthenticatesBearerTokenAndStoresUserInRequestContext() {
		AuthenticationTokenService authenticationTokenService = mock(AuthenticationTokenService.class);
		AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationTokenService);
		User user = new User("user@example.com", "hashed-password", "User");
		ReflectionTestUtils.setField(user, "id", 21L);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.addHeader("Authorization", "Bearer valid-token");
		when(authenticationTokenService.authenticate("valid-token")).thenReturn(user);

		assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
		assertThat(request.getAttribute(AuthenticatedUserRequest.USER_ATTRIBUTE)).isSameAs(user);
		assertThat(request.getAttribute(AuthenticatedUserRequest.USER_ID_ATTRIBUTE)).isEqualTo(21L);
		verify(authenticationTokenService).authenticate("valid-token");
	}

	@Test
	void preHandleRejectsMissingAuthorizationHeader() {
		AuthenticationTokenService authenticationTokenService = mock(AuthenticationTokenService.class);
		AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationTokenService);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verifyNoInteractions(authenticationTokenService);
	}

	@Test
	void preHandleRejectsBlankBearerToken() {
		AuthenticationTokenService authenticationTokenService = mock(AuthenticationTokenService.class);
		AuthenticationInterceptor interceptor = new AuthenticationInterceptor(authenticationTokenService);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		request.addHeader("Authorization", "Bearer   ");

		assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verifyNoInteractions(authenticationTokenService);
	}
}
