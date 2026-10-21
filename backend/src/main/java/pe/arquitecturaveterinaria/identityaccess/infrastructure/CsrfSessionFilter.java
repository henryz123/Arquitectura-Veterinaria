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

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.domain.TokenHasher;
import pe.arquitecturaveterinaria.shared.api.ApiErrorWriter;

@Component
public class CsrfSessionFilter extends OncePerRequestFilter {
	private final ApiErrorWriter apiErrorWriter;

	public CsrfSessionFilter(ApiErrorWriter apiErrorWriter) {
		this.apiErrorWriter = apiErrorWriter;
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
			if (header == null || !TokenHasher.matches(header, principal.session().csrfHash())) {
				apiErrorWriter.write(response, HttpStatus.FORBIDDEN, "CSRF_INVALID", "La solicitud no fue aceptada.");
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	private boolean requiresCsrf(HttpServletRequest request) {
		String method = request.getMethod();
		return !(HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method) || HttpMethod.OPTIONS.matches(method)
				|| request.getRequestURI().contains("/auth/tenants/"));
	}
}
