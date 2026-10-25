package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.appointmentmanagement.infrastructure.SchedulingJdbcRepository;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;

@Service
public class SchedulingService {

	private final SchedulingJdbcRepository repository;
	private final SchedulingAccessPolicy accessPolicy = new SchedulingAccessPolicy();

	public SchedulingService(SchedulingJdbcRepository repository) {
		this.repository = repository;
	}

	/**
	 * PostgreSQL receives only the hash resolved from the authenticated cookie.
	 * V020 verifies the session, role, tenant and target eligibility again at the
	 * database boundary.
	 */
	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<AssignableProfessional> listAssignableProfessionals(SessionPrincipal actor) {
		accessPolicy.requireSchedulingManager(actor);
		return repository.listAssignableProfessionals(actor.session().sessionTokenHash());
	}
}
