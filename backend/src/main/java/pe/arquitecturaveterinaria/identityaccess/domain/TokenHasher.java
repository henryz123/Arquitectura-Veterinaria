package pe.arquitecturaveterinaria.identityaccess.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class TokenHasher {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private TokenHasher() {
	}

	public static String newOpaqueToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no est\u00e1 disponible.", exception);
		}
	}

	public static boolean matches(String rawValue, String expectedHash) {
		return MessageDigest.isEqual(sha256(rawValue).getBytes(StandardCharsets.US_ASCII),
				expectedHash.getBytes(StandardCharsets.US_ASCII));
	}
}
