package dev.argusgraph.graph.application;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.argusgraph.graph.GraphAPI;
import dev.argusgraph.graph.PackageVersion;
import dev.argusgraph.graph.Purl;
import dev.argusgraph.graph.Vulnerability;
import dev.argusgraph.shared.exception.ResourceNotFoundException;

/**
 * Graph use cases in one application service: upsert nodes, link edges, read a node's
 * direct neighbourhood. It parses raw input into domain objects (which validate the
 * invariants and derive severity), delegates persistence to the {@link GraphRepository}
 * port, and owns the transaction boundary.
 *
 * <p>
 * It also implements {@link GraphAPI}, so the cross-module contract is served by the same
 * code that serves this module's own read controller.
 */
@Service
@org.jmolecules.ddd.annotation.Service
@RequiredArgsConstructor
@Transactional
public class GraphService implements GraphAPI {

	private final GraphRepository graph;

	@Override
	public PackageVersionSnapshot upsertPackageVersion(String purl) {
		PackageVersion saved = this.graph.upsertPackageVersion(PackageVersion.create(Purl.parse(purl)));
		return new PackageVersionSnapshot(saved.getPurl(), saved.getPackagePurl(), saved.getVersion());
	}

	@Override
	public PackageSnapshot upsertPackage(String purl) {
		Purl parsed = Purl.parse(purl);
		this.graph.upsertPackage(parsed.packageKey(), parsed.type(), parsed.namespace(), parsed.name());
		return new PackageSnapshot(parsed.packageKey(), parsed.type(), parsed.namespace(), parsed.name());
	}

	@Override
	public VulnerabilitySnapshot upsertVulnerability(VulnerabilityInput input) {
		Vulnerability saved = this.graph.upsertVulnerability(Vulnerability.create(input.id(), input.modified(),
				input.published(), input.withdrawn(), input.summary(), input.details(), input.aliases(),
				input.related(), input.upstream(), toSeverities(input), toReferences(input)));
		return new VulnerabilitySnapshot(saved.getId(), saved.getSeverity(), saved.getCvssScore());
	}

	@Override
	public void linkDependency(String fromPurl, String toPurl, String scope) {
		PackageVersion from = this.graph.upsertPackageVersion(PackageVersion.create(Purl.parse(fromPurl)));
		PackageVersion to = this.graph.upsertPackageVersion(PackageVersion.create(Purl.parse(toPurl)));
		this.graph.linkDependency(from.getPurl(), to.getPurl(), scope);
	}

	@Override
	public void linkAffects(String vulnerabilityId, String purl) {
		String id = Vulnerability.normalizeId(vulnerabilityId);
		PackageVersion target = this.graph.upsertPackageVersion(PackageVersion.create(Purl.parse(purl)));
		this.graph.linkAffects(id, target.getPurl());
	}

	@Override
	public void linkAffectsPackage(String vulnerabilityId, String packagePurl, String rangesJson) {
		String id = Vulnerability.normalizeId(vulnerabilityId);
		PackageSnapshot target = upsertPackage(packagePurl);
		this.graph.linkAffectsPackage(id, target.purl(), rangesJson);
	}

	/** Read a package version with its direct dependencies and known vulnerabilities. */
	@Transactional(readOnly = true)
	public PackageVersionDetails getPackageVersion(String purl) {
		String versionKey = Purl.parse(purl).versionKey();
		return this.graph.findPackageVersion(versionKey)
			.orElseThrow(() -> new ResourceNotFoundException(PackageVersion.class, versionKey));
	}

	private static List<Vulnerability.Severity> toSeverities(VulnerabilityInput input) {
		return input.severities() == null ? List.of()
				: input.severities().stream().map(s -> new Vulnerability.Severity(s.type(), s.vector())).toList();
	}

	private static List<Vulnerability.Reference> toReferences(VulnerabilityInput input) {
		return input.references() == null ? List.of()
				: input.references().stream().map(r -> new Vulnerability.Reference(r.type(), r.url())).toList();
	}

}
