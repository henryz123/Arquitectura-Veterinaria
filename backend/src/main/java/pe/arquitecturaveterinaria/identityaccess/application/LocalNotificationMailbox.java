package pe.arquitecturaveterinaria.identityaccess.application;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Development/test delivery adapter. It never logs a link or exposes one through
 * HTTP. The raw link exists only in process memory long enough for an integration
 * test to exercise the same verification endpoint a real mail provider would use.
 */
@Component
public class LocalNotificationMailbox {

	private final Map<UUID, DeliveredLink> deliveredLinks = new ConcurrentHashMap<>();

	public void accept(UUID relatedResourceId, String purpose, URI link) {
		deliveredLinks.put(relatedResourceId, new DeliveredLink(purpose, link, Instant.now()));
	}

	public DeliveredLink requireLatest(UUID relatedResourceId) {
		DeliveredLink delivery = deliveredLinks.get(relatedResourceId);
		if (delivery == null) {
			throw new IllegalStateException("No existe una entrega local para la prueba.");
		}
		return delivery;
	}

	public void discard(UUID relatedResourceId) {
		deliveredLinks.remove(relatedResourceId);
	}

	public record DeliveredLink(String purpose, URI link, Instant deliveredAt) { }
}
