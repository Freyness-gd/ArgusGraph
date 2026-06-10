package dev.argusgraph.graph;

import lombok.Getter;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/**
 * A concrete, resolved version of a package — the node vulnerability edges attach to and
 * dependency edges run between (graph model A: version-level graph).
 *
 * <p>
 * Identity is the canonical versioned purl (a natural key), a documented deviation from
 * the template's fresh-UUID convention: purls are globally unique by construction and
 * make cross-source merging trivial. The versionless {@code packagePurl} keys the parent
 * {@code Package} node, which the persistence layer maintains alongside this node.
 */
@Getter
@AggregateRoot
public class PackageVersion {

	/** Canonical purl WITH version, e.g. {@code pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1}. */
	@Identity
	private final String purl;

	/** Canonical purl WITHOUT version — key of the parent {@code Package} node. */
	private final String packagePurl;

	private final String type;

	/** Nullable — e.g. unscoped npm packages have no namespace. */
	private final String namespace;

	private final String name;

	private final String version;

	private PackageVersion(String purl, String packagePurl, String type, String namespace, String name,
			String version) {
		this.purl = purl;
		this.packagePurl = packagePurl;
		this.type = type;
		this.namespace = namespace;
		this.name = name;
		this.version = version;
	}

	// --- FACTORIES ---

	/** New package version from a parsed purl; the purl must carry a version. */
	public static PackageVersion create(Purl purl) {
		String versionKey = purl.versionKey(); // throws BusinessRuleException when versionless
		return new PackageVersion(versionKey, purl.packageKey(), purl.type(), purl.namespace(), purl.name(),
				purl.version());
	}

	/** Rebuild from persistence. Trusts the stored state — no re-validation. */
	public static PackageVersion reconstitute(String purl, String packagePurl, String type, String namespace,
			String name, String version) {
		return new PackageVersion(purl, packagePurl, type, namespace, name, version);
	}

}
