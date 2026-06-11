package dev.argusgraph.project.application;

import org.springframework.data.relational.core.mapping.Table;

/** One canonical version-level purl of a project. */
@Table("project_dependency")
public record ProjectDependency(String purl) {
}
