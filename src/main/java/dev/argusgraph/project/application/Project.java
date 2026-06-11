package dev.argusgraph.project.application;

import java.time.Instant;
import java.util.Set;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One imported project: a name and the canonical version-level purls from its SBOM.
 * Spring Data JDBC aggregate — saving cascades the dependency set, deleting removes it.
 * Explicit names are uppercase: Spring Data JDBC quotes them verbatim, and H2 stores
 * the unquoted schema.sql identifiers in uppercase.
 */
@AggregateRoot
@Table("PROJECT")
public record Project(@Id Long id, String name, Instant createdAt,
		@MappedCollection(idColumn = "PROJECT_ID") Set<ProjectDependency> dependencies) {
}
