package pe.arquitecturaveterinaria.identityaccess.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

	@Test
	void validatesOnlyTheTokenThatProducedTheStoredHash() {
		String token = TokenHasher.newOpaqueToken();
		String hash = TokenHasher.sha256(token);

		assertTrue(TokenHasher.matches(token, hash));
		assertFalse(TokenHasher.matches(TokenHasher.newOpaqueToken(), hash));
	}
}
