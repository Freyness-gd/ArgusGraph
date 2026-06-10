package dev.argusgraph.ingest.application;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import dev.argusgraph.graph.GraphAPI;
import dev.argusgraph.graph.Purl;
import dev.argusgraph.ingest.api.OsvVulnerabilityRequest;

/**
 * Ingestion use cases: translate validated source input (OSV documents, dependency
 * links) into {@link GraphAPI} calls. When the scraper workers arrive, their flows
 * converge here.
 *
 * <p>
 * No transaction boundary here: the graph module owns it per call. OSV ingestion is
 * therefore not document-atomic — acceptable because every write is an idempotent
 * upsert, so replaying a document completes any partial ingest.
 */
@Service
@org.jmolecules.ddd.annotation.Service
@RequiredArgsConstructor
public class IngestService {

	private final GraphAPI graph;

	private final OsvMapper mapper;

	/**
	 * Ingest one OSV document: upsert the vulnerability, link enumerated affected
	 * versions as {@code AFFECTS} edges, and preserve raw ranges on
	 * {@code AFFECTS_PACKAGE} edges.
	 */
	public IngestOsvResult ingestOsv(OsvVulnerabilityRequest document) {
		GraphAPI.VulnerabilitySnapshot vulnerability = this.graph.upsertVulnerability(this.mapper.toInput(document));
		int versionsLinked = 0;
		int packagesWithRanges = 0;
		int skippedPackages = 0;
		for (OsvVulnerabilityRequest.OsvAffected affected : emptyIfNull(document.affected())) {
			Purl packagePurl = this.mapper.toPackagePurl(affected.pkg());
			if (packagePurl == null) {
				skippedPackages++;
				continue;
			}
			for (String version : emptyIfNull(affected.versions())) {
				String versionKey = Purl
					.of(packagePurl.type(), packagePurl.namespace(), packagePurl.name(), version)
					.versionKey();
				this.graph.linkAffects(vulnerability.id(), versionKey);
				versionsLinked++;
			}
			if (affected.ranges() != null && !affected.ranges().isEmpty()) {
				this.graph.linkAffectsPackage(vulnerability.id(), packagePurl.packageKey(),
						this.mapper.rangesJson(affected.ranges()));
				packagesWithRanges++;
			}
		}
		return new IngestOsvResult(vulnerability, versionsLinked, packagesWithRanges, skippedPackages);
	}

	/** Ingest a batch of OSV documents (e.g. a slice of an ecosystem dump). */
	public List<IngestOsvResult> ingestOsvBatch(List<OsvVulnerabilityRequest> documents) {
		return documents.stream().map(this::ingestOsv).toList();
	}

	public GraphAPI.PackageVersionSnapshot ingestPackageVersion(String purl) {
		return this.graph.upsertPackageVersion(purl);
	}

	public void ingestDependency(String fromPurl, String toPurl, String scope) {
		this.graph.linkDependency(fromPurl, toPurl, scope);
	}

	public void ingestAffects(String vulnerabilityId, String purl) {
		this.graph.linkAffects(vulnerabilityId, purl);
	}

	private static <T> List<T> emptyIfNull(List<T> values) {
		return values == null ? List.of() : values;
	}

	/** Outcome of one OSV document ingest. */
	public record IngestOsvResult(GraphAPI.VulnerabilitySnapshot vulnerability, int affectedVersionsLinked,
			int packagesWithRanges, int skippedPackages) {
	}

}
