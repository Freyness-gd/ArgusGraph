package dev.argusgraph.graph.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.argusgraph.graph.application.GraphService;

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

	@GetMapping("/stats")
	@Operation(summary = "Whole-graph counts: packages, versions, vulnerabilities, severity buckets")
	public GraphStatsResponse getStats() {
		return GraphStatsResponse.from(this.graph.getStats());
	}

}
