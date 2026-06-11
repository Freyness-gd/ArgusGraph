package dev.argusgraph.project.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.argusgraph.graph.GraphAPI;

/** Read model: a project with its on-demand match results, AFFECTED-first ordering. */
public record ProjectMatchDetails(Long id, String name, Instant createdAt, Summary summary,
		List<DependencyMatch> dependencies) {

	/** Aggregate counts for the header badges. */
	public record Summary(int dependencies, int affected, int clean, int unknown, int transitivelyAffected,
			Map<String, Long> bySeverity) {
	}

	/** One dependency: direct verdict, direct vulnerabilities, and transitive exposure. */
	public record DependencyMatch(String purl, Verdict verdict, List<GraphAPI.VulnerabilityRef> vulnerabilities,
			List<TransitiveVuln> transitive) {
	}

	/** A vulnerability reaching this dependency through its own dependencies, with depth. */
	public record TransitiveVuln(String id, String severity, Double cvssScore, String summary, int depth) {
	}

	/**
	 * No data is not safe: UNKNOWN means the graph has never seen the purl.
	 * TRANSITIVELY_AFFECTED: not directly affected, but exposed through a dependency.
	 */
	public enum Verdict {

		AFFECTED, TRANSITIVELY_AFFECTED, CLEAN, UNKNOWN

	}

}
