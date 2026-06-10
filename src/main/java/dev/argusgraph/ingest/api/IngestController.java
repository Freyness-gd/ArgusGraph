package dev.argusgraph.ingest.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.argusgraph.ingest.application.IngestService;

/**
 * Write surface of the knowledge graph: typed, validated input in — graph out.
 * Vulnerabilities arrive as OSV-schema documents (https://ossf.github.io/osv-schema/);
 * OSV is the lingua franca the scraper workers will speak too. Thin by design: it maps
 * HTTP to {@link IngestService} calls. All endpoints are idempotent upserts, so sources
 * can be replayed safely.
 */
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ingest", description = "Typed ingestion into the knowledge graph (idempotent upserts).")
public class IngestController {

	private final IngestService ingest;

	@PostMapping("/package-versions")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Upsert a package version (and its parent package) by purl")
	public IngestPackageVersionResponse upsertPackageVersion(
			@RequestBody @Validated IngestPackageVersionRequest request) {
		return IngestPackageVersionResponse.from(this.ingest.ingestPackageVersion(request.purl()));
	}

	@PostMapping("/vulnerabilities")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Ingest one OSV vulnerability document")
	public IngestVulnerabilityResponse ingestOsv(@RequestBody @Validated OsvVulnerabilityRequest document) {
		return IngestVulnerabilityResponse.from(this.ingest.ingestOsv(document));
	}

	@PostMapping("/vulnerabilities/batch")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Ingest a batch of OSV vulnerability documents")
	public List<IngestVulnerabilityResponse> ingestOsvBatch(
			@RequestBody List<@Valid OsvVulnerabilityRequest> documents) {
		return this.ingest.ingestOsvBatch(documents).stream().map(IngestVulnerabilityResponse::from).toList();
	}

	@PostMapping("/dependencies")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Record a DEPENDS_ON edge between two package versions (both upserted)")
	public void linkDependency(@RequestBody @Validated IngestDependencyRequest request) {
		this.ingest.ingestDependency(request.fromPurl(), request.toPurl(), request.scope());
	}

	@PostMapping("/affects")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Record an AFFECTS edge from a vulnerability to a package version (both upserted)")
	public void linkAffects(@RequestBody @Validated IngestAffectsRequest request) {
		this.ingest.ingestAffects(request.vulnerabilityId(), request.purl());
	}

}
