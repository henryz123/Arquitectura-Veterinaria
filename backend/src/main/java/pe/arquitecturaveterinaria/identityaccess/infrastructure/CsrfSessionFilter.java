package pe.arquitecturaveterinaria.identityaccess.infrastructure;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import pe.arquitecturaveterinaria.identityaccess.application.IdentityAccessService;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.shared.api.ApiErrorWriter;

@Component
public class CsrfSessionFilter extends OncePerRequestFilter {
	private final ApiErrorWriter apiErrorWriter;
	private final IdentityAccessService identityAccessService;

	public CsrfSessionFilter(ApiErrorWriter apiErrorWriter, IdentityAccessService identityAccessService) {
		this.apiErrorWriter = apiErrorWriter;
		this.identityAccessService = identityAccessService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (requiresCsrf(request)) {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
				apiErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Se requiere una sesi\u00f3n v\u00e1lida.");
				return;
			}
			String header = request.getHeader("X-CSRF-Token");
			if (!identityAccessService.isCsrfTokenValid(principal, header)) {
				apiErrorWriter.write(response, HttpStatus.FORBIDDEN, "CSRF_INVALID", "La solicitud no fue aceptada.");
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	private boolean requiresCsrf(HttpServletRequest request) {
		if (isCsrfBootstrapRequest(request)) {
			return false;
		}
		String method = request.getMethod();
		if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method)) {
			return false;
		}
		String uri = request.getRequestURI();
		return !(uri.startsWith("/api/v1/auth/tenants/") || uri.equals("/api/v1/auth/saas-sessions")
				|| uri.equals("/api/v1/auth/password-recovery-requests") || uri.equals("/api/v1/auth/password-recoveries")
				|| uri.startsWith("/api/v1/tenant-registrations/") || uri.equals("/api/v1/tenant-registrations")
				|| uri.equals("/api/v1/email-verifications") || uri.matches("/api/v1/tenants/[^/]+/client-registrations")
				|| uri.matches("/api/v1/tenants/[^/]+/client-verification-resends")
				|| uri.matches("/api/v1/tenants/[^/]+/administrative-recoveries") || uri.equals("/api/v1/ruc-reviews")
				|| uri.equals("/api/v1/staff-invitations/acceptance"));
	}

	private boolean isCsrfBootstrapRequest(HttpServletRequest request) {
		return HttpMethod.POST.matches(request.getMethod())
				&& "/api/v1/auth/session-csrf-token".equals(request.getRequestURI())
				&& "1".equals(request.getHeader("X-AV-CSRF-Bootstrap"));
	}
}
