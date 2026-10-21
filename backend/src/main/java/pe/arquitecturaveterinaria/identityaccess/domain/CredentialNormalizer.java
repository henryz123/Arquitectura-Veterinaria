package pe.arquitecturaveterinaria.identityaccess.domain;

import java.util.Locale;

public final class CredentialNormalizer {

	private CredentialNormalizer() {
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
