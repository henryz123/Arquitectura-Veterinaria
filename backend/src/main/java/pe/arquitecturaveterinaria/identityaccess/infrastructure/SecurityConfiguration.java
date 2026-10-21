package pe.arquitecturaveterinaria.identityaccess.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import pe.arquitecturaveterinaria.shared.api.ApiErrorWriter;

@Configuration
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, OpaqueSessionAuthenticationFilter sessionFilter,
			CsrfSessionFilter csrfFilter, ApiErrorWriter apiErrorWriter) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/api/v1/auth/tenants/*/staff-sessions", "/api/v1/auth/tenants/*/client-sessions").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) ->
						apiErrorWriter.write(response, org.springframework.http.HttpStatus.UNAUTHORIZED,
								"UNAUTHENTICATED", "Se requiere una sesi\u00f3n v\u00e1lida.")))
				.addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
				.addFilterAfter(csrfFilter, OpaqueSessionAuthenticationFilter.class)
				.build();
	}
}
