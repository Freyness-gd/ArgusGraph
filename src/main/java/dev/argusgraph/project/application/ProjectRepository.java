package dev.argusgraph.project.application;

import org.springframework.data.repository.ListCrudRepository;

/**
 * Aggregate store for imported projects. Spring Data JDBC derives the implementation;
 * the interface doubles as this module's persistence port.
 */
@org.jmolecules.ddd.annotation.Repository
public interface ProjectRepository extends ListCrudRepository<Project, Long> {
}
