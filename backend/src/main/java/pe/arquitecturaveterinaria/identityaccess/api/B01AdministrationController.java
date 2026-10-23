package pe.arquitecturaveterinaria.identityaccess.api;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.ActivationResult;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.ClientSpace;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.RegistrationResult;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.StaffAccountView;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.StaffInvitationResult;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.VerificationResult;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.B01JdbcRepository.AdministrativeRecovery;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.B01JdbcRepository.Organization;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.B01JdbcRepository.RucReview;
import pe.arquitecturaveterinaria.shared.api.ApiEnvelope;

@RestController
@RequestMapping("/api/v1")
@Validated
public class B01AdministrationController {

	private static final String REGISTRATION_GRANT_COOKIE = "AV_REGISTRATION_GRANT";
	private final B01AdministrationService service;
	private final boolean secureCookie;

	public B01AdministrationController(B01AdministrationService service,
			@Value("${av.security.secure-cookie}") boolean secureCookie) {
		this.service = service;
		this.secureCookie = secureCookie;
	}

	@PostMapping("/tenant-registrations")
	ResponseEntity<ApiEnvelope<RegistrationResult>> registerTenant(@Valid @RequestBody TenantRegistrationRequest request) {
		RegistrationResult result = service.registerTenant(new B01AdministrationService.TenantRegistrationCommand(
				request.commercialName(), request.commercialAddress(), request.commercialPhone(), request.commercialEmail(),
				request.initialAdministratorEmail(), request.ruc()));
		return ResponseEntity.created(URI.create("/api/v1/tenant-registrations/" + result.registrationId())).body(ApiEnvelope.of(result));
	}

	@PostMapping("/tenant-registrations/{registrationId}/resumptions")
	ResponseEntity<ApiEnvelope<RegistrationResult>> resumeTenantRegistration(@PathVariable UUID registrationId,
			@Valid @RequestBody RegistrationResumptionRequest request) {
		return ResponseEntity.accepted().body(ApiEnvelope.of(service.resumeRegistration(registrationId, request.initialAdministratorEmail())));
	}

	@PostMapping("/email-verifications")
	ResponseEntity<ApiEnvelope<VerificationStatus>> verifyEmail(@Valid @RequestBody OneTimeTokenRequest request) {
		VerificationResult result = service.verifyEmailLink(request.token());
		ResponseEntity.BodyBuilder response = ResponseEntity.ok();
		if (result.completionGrant() != null) {
			response.header(HttpHeaders.SET_COOKIE, registrationGrantCookie(result.relatedId(), result.completionGrant(), false).toString());
		}
		return response.body(ApiEnvelope.of(new VerificationStatus(result.status(), result.accountType().name(), result.relatedId())));
	}

	@PostMapping("/tenant-registrations/{registrationId}/initial-password")
	ResponseEntity<ApiEnvelope<ActivationResult>> setInitialPassword(@PathVariable UUID registrationId, HttpServletRequest httpRequest,
			@Valid @RequestBody PasswordRequest request) {
		ActivationResult result = service.setInitialAdministratorPassword(registrationId, cookieValue(httpRequest, REGISTRATION_GRANT_COOKIE), request.password());
		return ResponseEntity.status(HttpStatus.CREATED)
				.header(HttpHeaders.SET_COOKIE, registrationGrantCookie(registrationId, "", true).toString())
				.body(ApiEnvelope.of(result));
	}

	@PostMapping("/tenants/{tenantSlug}/client-registrations")
	ResponseEntity<Void> registerClient(@PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
			@Valid @RequestBody ClientRegistrationRequest request) {
		service.registerClient(tenantSlug, request.email(), request.password());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/tenants/{tenantSlug}/client-verification-resends")
	ResponseEntity<Void> resendClientVerification(@PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
			@Valid @RequestBody EmailRequest request) {
		service.resendClientVerification(tenantSlug, request.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/auth/password-recovery-requests")
	ResponseEntity<Void> requestPasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request) {
		service.requestPasswordRecovery(request.accountType(), request.tenantSlug(), request.email());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/auth/password-recoveries")
	ResponseEntity<Void> resetPassword(@Valid @RequestBody RecoveryCompletionRequest request) {
		service.resetPassword(request.token(), request.password());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/staff-accounts")
	ApiEnvelope<List<StaffAccountView>> listStaff(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(service.listStaff(principal));
	}

	@PostMapping("/staff-invitations")
	ResponseEntity<ApiEnvelope<StaffInvitationResult>> inviteStaff(@AuthenticationPrincipal SessionPrincipal principal,
			@Valid @RequestBody StaffInvitationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(service.inviteStaff(principal, request.email(), request.roles())));
	}

	@PostMapping("/staff-invitations/acceptance")
	ResponseEntity<Void> acceptStaffInvitation(@Valid @RequestBody RecoveryCompletionRequest request) {
		service.acceptStaffInvitation(request.token(), request.password());
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/staff-accounts/{principalId}/roles")
	ApiEnvelope<StaffAccountView> replaceStaffRoles(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID principalId,
			@Valid @RequestBody StaffRolesRequest request) {
		return ApiEnvelope.of(service.replaceStaffRoles(principal, principalId, request.roles()));
	}

	@PostMapping("/staff-accounts/{principalId}/deactivations")
	ResponseEntity<Void> deactivateStaff(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID principalId) {
		service.deactivateStaff(principal, principalId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/tenants")
	ApiEnvelope<List<Organization>> listOrganizations(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(service.listOrganizations(principal));
	}

	@PostMapping("/tenants/{tenantId}/suspensions")
	ResponseEntity<Void> suspendTenant(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID tenantId,
			@Valid @RequestBody TenantStatusChangeRequest request) {
		service.suspendTenant(principal, tenantId, request.reason(), request.confirmed());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/tenants/{tenantId}/reactivations")
	ResponseEntity<Void> reactivateTenant(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID tenantId,
			@Valid @RequestBody TenantStatusChangeRequest request) {
		service.reactivateTenant(principal, tenantId, request.reason(), request.confirmed());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/ruc-reviews")
	ResponseEntity<Void> requestRucReview(@Valid @RequestBody RucReviewRequest request) {
		service.requestRucReview(request.ruc(), request.requesterEmail(), request.contactChannel(), request.reason());
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/ruc-reviews")
	ApiEnvelope<List<RucReview>> listRucReviews(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(service.listRucReviews(principal));
	}

	@PatchMapping("/ruc-reviews/{reviewId}")
	ResponseEntity<Void> resolveRucReview(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID reviewId,
			@Valid @RequestBody RucReviewResolutionRequest request) {
		service.resolveRucReview(principal, reviewId, request.status(), request.reason(), request.releaseRuc(), request.confirmed());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/tenants/{tenantSlug}/administrative-recoveries")
	ResponseEntity<Void> requestAdministrativeRecovery(@PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
			@Valid @RequestBody AdministrativeRecoveryRequest request) {
		service.requestAdministrativeRecovery(tenantSlug, request.applicantEmail(), request.evidenceReference());
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/administrative-recoveries")
	ApiEnvelope<List<AdministrativeRecovery>> listAdministrativeRecoveries(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(service.listAdministrativeRecoveries(principal));
	}

	@PatchMapping("/administrative-recoveries/{recoveryId}")
	ResponseEntity<Void> reviewAdministrativeRecovery(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID recoveryId,
			@Valid @RequestBody AdministrativeRecoveryReviewRequest request) {
		service.reviewAdministrativeRecovery(principal, recoveryId, request.status(), request.reason(), request.legitimacyChecked());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/client-space")
	ApiEnvelope<ClientSpace> clientSpace(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(service.clientSpace(principal));
	}

	private ResponseCookie registrationGrantCookie(UUID registrationId, String value, boolean clear) {
		return ResponseCookie.from(REGISTRATION_GRANT_COOKIE, value).httpOnly(true).secure(secureCookie).sameSite("Lax")
				.path("/api/v1/tenant-registrations/" + registrationId).maxAge(clear ? Duration.ZERO : Duration.ofMinutes(15)).build();
	}

	private String cookieValue(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public record TenantRegistrationRequest(
			@NotBlank @Pattern(regexp = ".{2,160}") String commercialName,
			@NotBlank @Pattern(regexp = ".{3,240}") String commercialAddress,
			@NotBlank @Pattern(regexp = ".{5,40}") String commercialPhone,
			@NotBlank @Email String commercialEmail,
			@NotBlank @Email String initialAdministratorEmail,
			String ruc) { }
	public record RegistrationResumptionRequest(@NotBlank @Email String initialAdministratorEmail) { }
	public record OneTimeTokenRequest(@NotBlank String token) { }
	public record PasswordRequest(@NotBlank String password) { }
	public record ClientRegistrationRequest(@NotBlank @Email String email, @NotBlank String password) { }
	public record EmailRequest(@NotBlank @Email String email) { }
	public record PasswordRecoveryRequest(@NotNull AccountType accountType,
			String tenantSlug, @NotBlank @Email String email) { }
	public record RecoveryCompletionRequest(@NotBlank String token, @NotBlank String password) { }
	public record StaffInvitationRequest(@NotBlank @Email String email, @NotEmpty List<@NotBlank String> roles) { }
	public record StaffRolesRequest(@NotEmpty List<@NotBlank String> roles) { }
	public record TenantStatusChangeRequest(@NotBlank @Pattern(regexp = ".{3,500}") String reason, boolean confirmed) { }
	public record RucReviewRequest(@NotBlank String ruc, @NotBlank @Email String requesterEmail,
			@NotBlank @Pattern(regexp = ".{3,254}") String contactChannel, @NotBlank @Pattern(regexp = ".{3,500}") String reason) { }
	public record RucReviewResolutionRequest(@NotBlank String status, @NotBlank @Pattern(regexp = ".{3,500}") String reason,
			boolean releaseRuc, boolean confirmed) { }
	public record AdministrativeRecoveryRequest(@NotBlank @Email String applicantEmail,
			@NotBlank @Pattern(regexp = ".{3,300}") String evidenceReference) { }
	public record AdministrativeRecoveryReviewRequest(@NotBlank String status, @NotBlank @Pattern(regexp = ".{3,500}") String reason,
			boolean legitimacyChecked) { }
	public record VerificationStatus(String status, String accountType, UUID relatedId) { }
}
