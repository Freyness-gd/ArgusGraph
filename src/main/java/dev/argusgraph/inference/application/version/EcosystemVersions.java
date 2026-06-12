package dev.argusgraph.inference.application.version;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Picks a version comparator for an OSV range. {@code SEMVER}-typed ranges use SemVer;
 * {@code ECOSYSTEM} ranges use the package's ecosystem ordering (Maven only for now).
 * Anything else ({@code GIT}, unsupported ecosystems) yields empty — the caller records the
 * range as unresolved rather than guessing.
 */
public final class EcosystemVersions {

	private static final Comparator<String> MAVEN =
			Comparator.comparing(ComparableVersion::new);

	private EcosystemVersions() {
	}

	public static Optional<Comparator<String>> comparatorFor(String rangeType, String purlType) {
		if (rangeType == null) {
			return Optional.empty();
		}
		return switch (rangeType.toUpperCase(Locale.ROOT)) {
			case "SEMVER" -> Optional.of(SemverComparator.INSTANCE);
			case "ECOSYSTEM" -> "maven".equalsIgnoreCase(purlType) ? Optional.of(MAVEN) : Optional.empty();
			default -> Optional.empty();
		};
	}
}
