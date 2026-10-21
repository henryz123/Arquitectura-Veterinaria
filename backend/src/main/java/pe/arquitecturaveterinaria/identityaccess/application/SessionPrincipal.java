package pe.arquitecturaveterinaria.identityaccess.application;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;

public final class SessionPrincipal implements UserDetails {

	private final TenantSession session;

	public SessionPrincipal(TenantSession session) {
		this.session = session;
	}

	public TenantSession session() {
		return session;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return session.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
	}

	@Override
	public String getPassword() {
		return "";
	}

	@Override
	public String getUsername() {
		return session.accountId().toString();
	}

	public AccountType accountType() {
		return session.accountType();
	}
}
