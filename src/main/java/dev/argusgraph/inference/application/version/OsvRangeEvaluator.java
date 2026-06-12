package dev.argusgraph.inference.application.version;

import java.util.Comparator;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The R2 built-in predicate: is {@code version} affected by an advisory's OSV ranges?
 * Ranges are the verbatim JSON array stored on {@code AFFECTS_PACKAGE.ranges}. Events form
 * affected intervals: {@code introduced} opens, {@code fixed} closes exclusively,
 * {@code last_affected} closes inclusively. Ranges whose type/ecosystem has no comparator are
 * not guessed — if no supported range decides the version, the result is {@code UNRESOLVED}.
 */
public class OsvRangeEvaluator {

	/** Outcome of testing one version against one advisory's ranges for one package. */
	public enum Verdict {
		AFFECTED, NOT_AFFECTED, UNRESOLVED
	}

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public Verdict evaluate(String rangesJson, String purlType, String version) {
		JsonNode ranges;
		try {
			ranges = MAPPER.readTree(rangesJson);
		}
		catch (JacksonException ex) {
			return Verdict.UNRESOLVED;
		}
		if (!ranges.isArray() || ranges.isEmpty()) {
			return Verdict.UNRESOLVED;
		}
		boolean anySupported = false;
		for (JsonNode range : ranges) {
			Optional<Comparator<String>> cmp =
					EcosystemVersions.comparatorFor(range.path("type").asString(null), purlType);
			if (cmp.isEmpty()) {
				continue;
			}
			anySupported = true;
			if (affectedByRange(range, version, cmp.get())) {
				return Verdict.AFFECTED;
			}
		}
		return anySupported ? Verdict.NOT_AFFECTED : Verdict.UNRESOLVED;
	}

	private boolean affectedByRange(JsonNode range, String version, Comparator<String> cmp) {
		// Walk events in order; track the currently-open lower bound. "introduced" opens an
		// interval, "fixed"/"last_affected" close the open interval. "0" introduced = from start.
		String introduced = null;
		boolean lastInclusive = false;
		String upper = null;
		boolean open = false;
		for (JsonNode event : range.path("events")) {
			if (event.hasNonNull("introduced")) {
				if (open && inInterval(version, introduced, upper, lastInclusive, cmp)) {
					return true;
				}
				introduced = event.get("introduced").asString();
				upper = null;
				lastInclusive = false;
				open = true;
			}
			else if (event.hasNonNull("fixed")) {
				upper = event.get("fixed").asString();
				lastInclusive = false;
				if (inInterval(version, introduced, upper, lastInclusive, cmp)) {
					return true;
				}
				open = false;
			}
			else if (event.hasNonNull("last_affected")) {
				upper = event.get("last_affected").asString();
				lastInclusive = true;
				if (inInterval(version, introduced, upper, lastInclusive, cmp)) {
					return true;
				}
				open = false;
			}
		}
		return open && inInterval(version, introduced, null, false, cmp);
	}

	private boolean inInterval(String version, String introduced, String upper, boolean inclusiveUpper,
			Comparator<String> cmp) {
		if (introduced != null && !"0".equals(introduced) && cmp.compare(version, introduced) < 0) {
			return false;
		}
		if (upper == null) {
			return true; // open-ended interval (introduced with no close)
		}
		int u = cmp.compare(version, upper);
		return inclusiveUpper ? u <= 0 : u < 0;
	}
}
