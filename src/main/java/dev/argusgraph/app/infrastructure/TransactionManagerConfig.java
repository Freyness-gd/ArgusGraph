package dev.argusgraph.app.infrastructure;

import javax.sql.DataSource;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.jdbc.support.JdbcTransactionManager;

/**
 * Two stores, two explicit transaction managers. Without this, Boot's auto-config
 * ordering hands the Neo4j manager to EVERY unqualified {@code @Transactional} —
 * including the H2-backed project repositories — making JDBC writes autocommit and
 * coupling pure-H2 endpoints to Neo4j availability.
 *
 * <p>
 * Default ({@code transactionManager}, {@code @Primary}): JDBC/H2 — Spring Data JDBC
 * repositories and the project module bind to it unqualified. {@code @Primary} is what
 * makes "unqualified" work: with two {@code TransactionManager} beans, Spring resolves
 * unqualified {@code @Transactional} by type and needs a primary to disambiguate. The
 * graph module qualifies every {@code @Transactional} with {@code neo4jTransactionManager}.
 */
@Configuration
class TransactionManagerConfig {

	@Bean
	@Primary
	JdbcTransactionManager transactionManager(DataSource dataSource) {
		return new JdbcTransactionManager(dataSource);
	}

	@Bean
	Neo4jTransactionManager neo4jTransactionManager(Driver driver) {
		return new Neo4jTransactionManager(driver);
	}

}
