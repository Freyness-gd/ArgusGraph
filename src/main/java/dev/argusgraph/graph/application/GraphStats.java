package dev.argusgraph.graph.application;

import java.util.Map;

/**
 * Read model: whole-graph node counts plus the vulnerability count per severity bucket.
 * Vulnerabilities without a derived severity land in the {@code NONE} bucket.
 */
public record GraphStats(long packages, long packageVersions, long vulnerabilities,
		Map<String, Long> bySeverity) {
}
