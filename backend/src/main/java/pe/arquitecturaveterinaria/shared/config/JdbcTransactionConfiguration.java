package pe.arquitecturaveterinaria.shared.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Binds the JDBC adapters used by the identity boundary to one physical
 * connection. This keeps multi-step database operations atomic without
 * relying on an incidental JPA transaction.
 */
@Configuration
public class JdbcTransactionConfiguration {

	@Bean("jdbcTransactionManager")
	PlatformTransactionManager jdbcTransactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}
}
