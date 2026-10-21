package pe.arquitecturaveterinaria.identityaccess.infrastructure;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import pe.arquitecturaveterinaria.identityaccess.application.IdentityAccessService;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;

@Component
public class OpaqueSessionAuthenticationFilter extends OncePerRequestFilter {

	private static final String SESSION_COOKIE = "AV_SESSION";
	private final IdentityAccessService identityAccessService;

	public OpaqueSessionAuthenticationFilter(IdentityAccessService identityAccessService) {
		this.identityAccessService = identityAccessService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = findCookie(request, SESSION_COOKIE);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			identityAccessService.resolve(token).ifPresent(session -> {
				SessionPrincipal principal = new SessionPrincipal(session);
				SecurityContextHolder.getContext().setAuthentication(
						new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
			});
		}
		filterChain.doFilter(request, response);
	}

	private String findCookie(HttpServletRequest request, String name) {
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
}
