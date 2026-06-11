package dev.argusgraph.project.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.project.application.ProjectMatchDetails;

/** Read response: a project with its live vulnerability match. */
@Schema(name = "ProjectDetail")
public record ProjectDetailResponse(@Schema(example = "1") Long id, @Schema(example = "demo-app") String name,
		Instant createdAt, SummaryResponse summary, List<DependencyResponse> dependencies) {

	public static ProjectDetailResponse from(ProjectMatchDetails details) {
		return new ProjectDetailResponse(details.id(), details.name(), details.createdAt(),
				SummaryResponse.from(details.summary()),
				details.dependencies().stream().map(DependencyResponse::from).toList());
	}

	/** Header badge counts. */
	@Schema(name = "ProjectMatchSummary")
	public record SummaryResponse(@Schema(example = "412") int dependencies, @Schema(example = "7") int affected,
			@Schema(example = "380") int clean, @Schema(example = "25") int unknown,
			@Schema(example = "12") int transitivelyAffected, Map<String, Long> bySeverity) {

		static SummaryResponse from(ProjectMatchDetails.Summary summary) {
			return new SummaryResponse(summary.dependencies(), summary.affected(), summary.clean(),
					summary.unknown(), summary.transitivelyAffected(), summary.bySeverity());
		}
	}

	/** One dependency row with verdict, direct vulnerabilities, and transitive exposure. */
	@Schema(name = "ProjectDependencyMatch")
	public record DependencyResponse(
			@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1") String purl,
			@Schema(example = "AFFECTED") String verdict, List<VulnerabilityResponse> vulnerabilities,
			List<TransitiveResponse> transitive) {

		static DependencyResponse from(ProjectMatchDetails.DependencyMatch match) {
			return new DependencyResponse(match.purl(), match.verdict().name(),
					match.vulnerabilities()
						.stream()
						.map(v -> new VulnerabilityResponse(v.id(), v.severity(), v.cvssScore(), v.summary()))
						.toList(),
					match.transitive()
						.stream()
						.map(v -> new TransitiveResponse(v.id(), v.severity(), v.cvssScore(), v.summary(), v.depth()))
						.toList());
		}
	}

	/** One vulnerability reaching a dependency transitively, with dependency depth. */
	@Schema(name = "ProjectTransitiveVulnerability")
	public record TransitiveResponse(@Schema(example = "GHSA-jfh8-c2jp-5v3q") String id,
			@Schema(example = "CRITICAL") String severity, @Schema(example = "9.8") Double cvssScore,
			@Schema(example = "RCE in a transitive dependency") String summary, @Schema(example = "2") int depth) {
	}

	/** One vulnerability behind an AFFECTED dependency. */
	@Schema(name = "ProjectVulnerability")
	public record VulnerabilityResponse(@Schema(example = "GHSA-jfh8-c2jp-5v3q") String id,
			@Schema(example = "CRITICAL") String severity, @Schema(example = "10.0") Double cvssScore,
			@Schema(example = "Remote code injection in Log4j") String summary) {
	}

}
