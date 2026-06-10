package dev.argusgraph.graph;

import java.util.Objects;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import org.jmolecules.ddd.annotation.ValueObject;

import dev.argusgraph.shared.exception.BusinessRuleException;

/**
 * Canonical package URL (purl, see the purl-spec) — the natural key of the knowledge
 * graph. Wraps {@code packageurl-java} parsing and derives the two node keys:
 * {@link #packageKey()} (versionless — the {@code Package} node) and
 * {@link #versionKey()} (versioned — the {@code PackageVersion} node).
 *
 * <p>
 * Qualifiers and subpaths are deliberately dropped from both keys so the same component
 * reported by different sources (OSV, NVD, deps.dev, ...) lands on the same node.
 */
@ValueObject
public final class Purl {

	private final PackageURL parsed;

	private Purl(PackageURL parsed) {
		this.parsed = parsed;
	}

	/**
	 * Build a purl from components (version may be null). Used by source adapters that
	 * receive ecosystem/name pairs instead of ready-made purls.
	 */
	public static Purl of(String type, String namespace, String name, String version) {
		try {
			return new Purl(new PackageURL(type, namespace, name, version, null, null));
		}
		catch (MalformedPackageURLException ex) {
			throw new BusinessRuleException("Invalid purl components (type=%s, namespace=%s, name=%s, version=%s): %s"
				.formatted(type, namespace, name, version, ex.getMessage()));
		}
	}

	/** Parse and canonicalise. Throws {@link BusinessRuleException} on malformed input. */
	public static Purl parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new BusinessRuleException("purl must not be blank.");
		}
		try {
			return new Purl(new PackageURL(raw.trim()));
		}
		catch (MalformedPackageURLException ex) {
			throw new BusinessRuleException("Invalid purl '%s': %s".formatted(raw, ex.getMessage()));
		}
	}

	public boolean hasVersion() {
		return this.parsed.getVersion() != null && !this.parsed.getVersion().isBlank();
	}

	/** Canonical purl without version/qualifiers/subpath — key of the {@code Package} node. */
	public String packageKey() {
		return rebuild(null);
	}

	/**
	 * Canonical purl with version but without qualifiers/subpath — key of the
	 * {@code PackageVersion} node. Throws when the purl carries no version.
	 */
	public String versionKey() {
		if (!hasVersion()) {
			throw new BusinessRuleException("purl '%s' must include a version.".formatted(canonical()));
		}
		return rebuild(this.parsed.getVersion());
	}

	public String type() {
		return this.parsed.getType();
	}

	public String namespace() {
		return this.parsed.getNamespace();
	}

	public String name() {
		return this.parsed.getName();
	}

	public String version() {
		return this.parsed.getVersion();
	}

	/** Full canonical form, including qualifiers/subpath if present. */
	public String canonical() {
		return this.parsed.canonicalize();
	}

	private String rebuild(String version) {
		try {
			return new PackageURL(this.parsed.getType(), this.parsed.getNamespace(), this.parsed.getName(), version,
					null, null)
				.canonicalize();
		}
		catch (MalformedPackageURLException ex) {
			throw new IllegalStateException("Could not canonicalise an already-parsed purl.", ex);
		}
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Purl purl && canonical().equals(purl.canonical());
	}

	@Override
	public int hashCode() {
		return Objects.hash(canonical());
	}

	@Override
	public String toString() {
		return canonical();
	}

}
