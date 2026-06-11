package dev.argusgraph.graph.application;

import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.argusgraph.graph.GraphAPI;
import dev.argusgraph.graph.PackageVersion;
import dev.argusgraph.graph.Purl;
import dev.argusgraph.graph.Vulnerability;
import dev.argusgraph.shared.exception.BusinessRuleException;
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

	private static final int MAX_PAGE_SIZE = 100;

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

	@Override
	public void attachEmbedding(String vulnerabilityId, float[] embedding) {
		String id = Vulnerability.normalizeId(vulnerabilityId);
		if (embedding == null || embedding.length == 0) {
			throw new BusinessRuleException("Embedding vector must not be empty.");
		}
		this.graph.attachEmbedding(id, embedding);
	}

	/** Read a package version with its direct dependencies and known vulnerabilities. */
	@Transactional(readOnly = true)
	public PackageVersionDetails getPackageVersion(String purl) {
		String versionKey = Purl.parse(purl).versionKey();
		return this.graph.findPackageVersion(versionKey)
			.orElseThrow(() -> new ResourceNotFoundException(PackageVersion.class, versionKey));
	}

	/** Whole-graph counts for the dashboard. */
	@Transactional(readOnly = true)
	public GraphStats getStats() {
		return this.graph.fetchStats();
	}

	/**
	 * Wipe the whole graph (all nodes and relationships); constraints stay. Suspends the
	 * surrounding Spring transaction ({@code NOT_SUPPORTED}) because the adapter's
	 * batched delete uses {@code CALL ... IN TRANSACTIONS}, which Neo4j only allows in
	 * implicit (auto-commit) transactions.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public long resetGraph() {
		return this.graph.wipeAll();
	}

	/** One page of the vulnerability browse table; blank filters mean "all". */
	@Transactional(readOnly = true)
	public VulnerabilityPage findVulnerabilities(String severity, String q, int page, int size) {
		String severityFilter = (severity == null || severity.isBlank()) ? null
				: severity.trim().toUpperCase(Locale.ROOT);
		String text = (q == null || q.isBlank()) ? null : q.trim();
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		return this.graph.findVulnerabilities(severityFilter, text, safePage, safeSize);
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
