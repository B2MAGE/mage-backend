package com.bdmage.mage_backend.client;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;

import com.bdmage.mage_backend.exception.GoogleAuthenticationUnavailableException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.springframework.stereotype.Component;

@Component
public class GoogleApiClientTokenVerifier implements GoogleTokenVerifier {

	private final GoogleIdTokenVerifier googleIdTokenVerifier;

	public GoogleApiClientTokenVerifier(GoogleIdTokenVerifier googleIdTokenVerifier) {
		this.googleIdTokenVerifier = googleIdTokenVerifier;
	}

	@Override
	public Optional<VerifiedGoogleToken> verify(String idToken) {
		try {
			GoogleIdToken googleIdToken = this.googleIdTokenVerifier.verify(idToken);

			if (googleIdToken == null) {
				return Optional.empty();
			}

			GoogleIdToken.Payload payload = googleIdToken.getPayload();

			return Optional.of(new VerifiedGoogleToken(
					payload.getSubject(),
					payload.getEmail(),
					Boolean.TRUE.equals(payload.getEmailVerified()),
					(String) payload.get("name")));
		} catch (GeneralSecurityException | IOException ex) {
			throw new GoogleAuthenticationUnavailableException(
					"Google token verification is temporarily unavailable.",
					ex);
		}
	}
}
