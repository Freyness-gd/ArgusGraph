package dev.argusgraph.graph.api;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.argusgraph.graph.application.GraphService;
import dev.argusgraph.shared.exception.BusinessRuleException;

/**
 * Read surface of the knowledge graph. Thin by design: it maps HTTP to
 * {@link GraphService} calls and converts read models to response DTOs. The purl travels
 * as a query parameter because canonical purls contain slashes.
 */
@RestController
@RequestMapping("/graph")
@RequiredArgsConstructor
@Validated
@Tag(name = "Graph", description = "Read access to the knowledge graph.")
public class GraphController {

	private final GraphService graph;

	@GetMapping("/package-versions")
	@Operation(summary = "Get a package version with its direct dependencies and known vulnerabilities")
	public PackageVersionResponse getPackageVersion(
			@RequestParam @NotBlank(message = "purl must not be blank") String purl) {
		return PackageVersionResponse.from(this.graph.getPackageVersion(purl));
	}

	@GetMapping("/neighbourhood")
	@Operation(summary = "A package-version's direct neighbourhood: dependencies, dependents, and vulnerabilities")
	public NeighbourhoodResponse getNeighbourhood(
			@RequestParam @NotBlank(message = "purl must not be blank") String purl) {
		return NeighbourhoodResponse.from(this.graph.getNeighbourhood(purl));
	}

	@GetMapping("/packages")
	@Operation(summary = "Page through packages, most-affected first, with an optional text filter")
	public PackagePageResponse listPackages(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String q) {
		return PackagePageResponse.from(this.graph.findPackages(q, page, size));
	}

	@GetMapping("/packages/detail")
	@Operation(summary = "One package with all its versions and the vulnerabilities affecting each")
	public PackageDetailResponse getPackage(
			@RequestParam @NotBlank(message = "purl must not be blank") String purl) {
		return PackageDetailResponse.from(this.graph.getPackage(purl));
	}

	@GetMapping("/stats")
	@Operation(summary = "Whole-graph counts: packages, versions, vulnerabilities, severity buckets")
	public GraphStatsResponse getStats() {
		return GraphStatsResponse.from(this.graph.getStats());
	}

	@GetMapping("/vulnerabilities")
	@Operation(summary = "Page through vulnerabilities, newest first, with optional severity and text filters")
	public VulnerabilityPageResponse listVulnerabilities(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String severity,
			@RequestParam(required = false) String q) {
		return VulnerabilityPageResponse.from(this.graph.findVulnerabilities(severity, q, page, size));
	}

	@GetMapping("/stats/vulnerability-trend")
	@Operation(summary = "Vulnerabilities published per time bucket over a date range (default: past month)")
	public VulnerabilityTrendResponse vulnerabilityTrend(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return VulnerabilityTrendResponse.from(this.graph.getVulnerabilityTrend(from, to));
	}

	@GetMapping("/stats/top-packages")
	@Operation(summary = "Packages ranked by distinct affecting vulnerabilities")
	public List<TopPackageResponse> topPackages(@RequestParam(defaultValue = "10") int limit) {
		return this.graph.topAffectedPackages(limit).stream().map(TopPackageResponse::from).toList();
	}

	@PostMapping("/reset")
	@Operation(summary = "DESTRUCTIVE: delete every node and relationship in the graph "
			+ "(constraints survive, Modulith event publications are wiped too). "
			+ "Requires body {\"confirm\": \"WIPE\"}.")
	public GraphResetResponse reset(@RequestBody GraphResetRequest request) {
		if (!"WIPE".equals(request.confirm())) {
			throw new BusinessRuleException("Graph reset requires confirm: WIPE.");
		}
		return new GraphResetResponse(this.graph.resetGraph());
	}

}
