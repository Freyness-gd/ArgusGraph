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
 */
@AggregateRoot
@Table("project")
public record Project(@Id Long id, String name, Instant createdAt,
		@MappedCollection(idColumn = "project_id") Set<ProjectDependency> dependencies) {
}
