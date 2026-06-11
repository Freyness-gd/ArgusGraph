package dev.argusgraph.project.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.argusgraph.graph.GraphAPI;

/** Read model: a project with its on-demand match results, AFFECTED-first ordering. */
public record ProjectMatchDetails(Long id, String name, Instant createdAt, Summary summary,
		List<DependencyMatch> dependencies) {

	/** Aggregate counts for the header badges. */
	public record Summary(int dependencies, int affected, int clean, int unknown, Map<String, Long> bySeverity) {
	}

	/** One dependency with its verdict and the vulnerabilities behind it. */
	public record DependencyMatch(String purl, Verdict verdict, List<GraphAPI.VulnerabilityRef> vulnerabilities) {
	}

	/** No data is not safe: UNKNOWN means the graph has never seen the purl. */
	public enum Verdict {

		AFFECTED, CLEAN, UNKNOWN

	}

}
