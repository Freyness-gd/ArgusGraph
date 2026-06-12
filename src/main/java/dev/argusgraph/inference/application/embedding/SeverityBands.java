package dev.argusgraph.inference.application.embedding;

/** CVSS qualitative bands, replicated here so inference stays free of graph internals. */
public final class SeverityBands {

	private SeverityBands() {
	}

	public static String of(double score) {
		if (score >= 9.0) {
			return "CRITICAL";
		}
		if (score >= 7.0) {
			return "HIGH";
		}
		if (score >= 4.0) {
			return "MEDIUM";
		}
		if (score > 0.0) {
			return "LOW";
		}
		return "NONE";
	}
}
