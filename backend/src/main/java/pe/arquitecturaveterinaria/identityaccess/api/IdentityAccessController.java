package pe.arquitecturaveterinaria.identityaccess.api;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import pe.arquitecturaveterinaria.identityaccess.application.IdentityAccessService;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.shared.api.ApiEnvelope;
import pe.arquitecturaveterinaria.shared.api.ApiException;

@RestController
@RequestMapping("/api/v1")
@Validated
public class IdentityAccessController {

	private final IdentityAccessService identityAccessService;
	private final boolean secureCookie;

	public IdentityAccessController(IdentityAccessService identityAccessService,
			@Value("${av.security.secure-cookie}") boolean secureCookie) {
		this.identityAccessService = identityAccessService;
		this.secureCookie = secureCookie;
	}

	@PostMapping("/auth/tenants/{tenantSlug}/staff-sessions")
	ResponseEntity<ApiEnvelope<LoginResult>> createStaffSession(@PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
			@Valid @RequestBody LoginRequest request) {
		IdentityAccessService.CreatedSession created = identityAccessService.authenticateStaff(tenantSlug, request.email(), request.password());
		ResponseCookie cookie = ResponseCookie.from("AV_SESSION", created.sessionToken())
				.httpOnly(true).secure(secureCookie).sameSite("Lax").path("/")
				.maxAge(Duration.between(java.time.Instant.now(), created.expiresAt())).build();
		return ResponseEntity.status(HttpStatus.CREATED)
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(ApiEnvelope.of(new LoginResult(created.csrfToken(), toContext(created.context()))));
	}

	@PostMapping("/auth/tenants/{tenantSlug}/client-sessions")
	ResponseEntity<ApiEnvelope<LoginResult>> createClientSession(@PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
			@Valid @RequestBody LoginRequest request) {
		IdentityAccessService.CreatedSession created = identityAccessService.authenticateClient(tenantSlug, request.email(), request.password());
		return sessionCreated(created);
	}

	@PostMapping("/auth/saas-sessions")
	ResponseEntity<ApiEnvelope<LoginResult>> createSaasSession(@Valid @RequestBody LoginRequest request) {
		return sessionCreated(identityAccessService.authenticateSaas(request.email(), request.password()));
	}

	@GetMapping("/session-context")
	ApiEnvelope<SessionContext> sessionContext(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(toContext(principal.session()));
	}

	@GetMapping("/tenant-profile/{tenantSlug}")
	ApiEnvelope<TenantProfile> tenantProfile(@PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
			@AuthenticationPrincipal SessionPrincipal principal) {
		TenantSession session = principal.session();
		if (session.tenantSlug() == null || !session.tenantSlug().equals(tenantSlug)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "El recurso solicitado no est\u00e1 disponible.");
		}
		return ApiEnvelope.of(new TenantProfile(session.tenantSlug(), session.tenantCommercialName(), session.tenantStatus()));
	}

	@DeleteMapping("/auth/session")
	ResponseEntity<Void> deleteSession(@AuthenticationPrincipal SessionPrincipal principal) {
		identityAccessService.logout(principal);
		ResponseCookie cookie = ResponseCookie.from("AV_SESSION", "").httpOnly(true).secure(secureCookie)
				.sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
	}

	private SessionContext toContext(TenantSession session) {
		TenantProfile tenant = session.tenantSlug() == null ? null
				: new TenantProfile(session.tenantSlug(), session.tenantCommercialName(), session.tenantStatus());
		return new SessionContext(session.accountType().name(), tenant, session.roles());
	}

	private ResponseEntity<ApiEnvelope<LoginResult>> sessionCreated(IdentityAccessService.CreatedSession created) {
		ResponseCookie cookie = ResponseCookie.from("AV_SESSION", created.sessionToken())
				.httpOnly(true).secure(secureCookie).sameSite("Lax").path("/")
				.maxAge(Duration.between(java.time.Instant.now(), created.expiresAt())).build();
		return ResponseEntity.status(HttpStatus.CREATED)
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(ApiEnvelope.of(new LoginResult(created.csrfToken(), toContext(created.context()))));
	}

	public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
	}

	public record LoginResult(String csrfToken, SessionContext context) {
	}

	public record SessionContext(String accountType, TenantProfile tenant, List<String> roles) {
	}

	public record TenantProfile(String slug, String commercialName, String status) {
	}
}
