package pe.arquitecturaveterinaria.identityaccess.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CredentialNormalizerTest {

	@Test
	void normalizesEmailWithoutLocaleSideEffects() {
		assertEquals("personal@veterinaria-sintetica.test",
				CredentialNormalizer.normalizeEmail(" Personal@Veterinaria-Sintetica.Test "));
	}
}
