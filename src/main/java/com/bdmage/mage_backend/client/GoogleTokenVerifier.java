package com.bdmage.mage_backend.client;

import java.util.Optional;

public interface GoogleTokenVerifier {

	Optional<VerifiedGoogleToken> verify(String idToken);

	record VerifiedGoogleToken(String subject, String email, boolean emailVerified, String displayName) {
	}
}
