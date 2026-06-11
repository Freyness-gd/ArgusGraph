package dev.argusgraph.project.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.argusgraph.graph.GraphAPI;
import dev.argusgraph.shared.exception.BusinessRuleException;
import dev.argusgraph.shared.exception.ResourceNotFoundException;

/**
 * Project use cases: import an SBOM into H2, list/delete projects, and assemble the
 * on-demand vulnerability match by calling the graph module's published contract.
 */
@Service
@org.jmolecules.ddd.annotation.Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

	private static final List<String> SEVERITY_ORDER = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "NONE");

	private final ProjectRepository projects;

	private final SbomParser parser;

	private final GraphAPI graph;

	/** Import one CycloneDX SBOM; explicit name wins over SBOM metadata. */
	public ImportResult importSbom(String name, String sbomJson) {
		SbomParser.ParsedSbom sbom = this.parser.parse(sbomJson);
		String resolved = firstNonBlank(name, sbom.defaultName());
		if (resolved == null || resolved.isBlank()) {
			throw new BusinessRuleException(
					"Project name required: pass ?name= or include metadata.component.name in the SBOM.");
		}
		if (resolved.length() > 255) {
			throw new BusinessRuleException("Project name too long (max 255 characters).");
		}
		Project saved = this.projects.save(new Project(null, resolved, Instant.now(),
				sbom.purls().stream().map(ProjectDependency::new).collect(Collectors.toSet())));
		return new ImportResult(saved.id(), saved.name(), saved.dependencies().size(), sbom.skipped());
	}

	@Transactional(readOnly = true)
	public List<ProjectSummary> list() {
		return this.projects.findAll()
			.stream()
			.sorted(Comparator.comparing(Project::createdAt).reversed())
			.map(p -> new ProjectSummary(p.id(), p.name(), p.createdAt(), p.dependencies().size()))
			.toList();
	}

	/** Project + live match against the graph, AFFECTED-first. */
	@Transactional(readOnly = true)
	public ProjectMatchDetails get(long id) {
		Project project = this.projects.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException(Project.class, String.valueOf(id)));
		List<String> purls = project.dependencies().stream().map(ProjectDependency::purl).toList();
		Map<String, GraphAPI.PurlMatch> byPurl = this.graph.matchPackageVersions(purls)
			.stream()
			.collect(Collectors.toMap(GraphAPI.PurlMatch::purl, match -> match, (first, duplicate) -> first));

		List<ProjectMatchDetails.DependencyMatch> dependencies = purls.stream().map(purl -> {
			GraphAPI.PurlMatch match = byPurl.get(purl);
			ProjectMatchDetails.Verdict verdict = verdictOf(match);
			List<GraphAPI.VulnerabilityRef> vulnerabilities = match == null ? List.of()
					: match.vulnerabilities()
						.stream()
						.sorted(Comparator.comparingInt(v -> severityRank(v.severity())))
						.toList();
			return new ProjectMatchDetails.DependencyMatch(purl, verdict, vulnerabilities);
		})
			.sorted(Comparator
				.comparingInt(
						(ProjectMatchDetails.DependencyMatch d) -> switch (d.verdict()) {
							case AFFECTED -> 0;
							case UNKNOWN -> 1;
							case CLEAN -> 2;
						})
				.thenComparingInt(d -> d.vulnerabilities().isEmpty() ? Integer.MAX_VALUE
						: severityRank(d.vulnerabilities().get(0).severity()))
				.thenComparing(ProjectMatchDetails.DependencyMatch::purl))
			.toList();

		return new ProjectMatchDetails(project.id(), project.name(), project.createdAt(),
				summarize(dependencies), dependencies);
	}

	public void delete(long id) {
		if (!this.projects.existsById(id)) {
			throw new ResourceNotFoundException(Project.class, String.valueOf(id));
		}
		this.projects.deleteById(id);
	}

	private static ProjectMatchDetails.Verdict verdictOf(GraphAPI.PurlMatch match) {
		if (match == null || !match.knownToGraph()) {
			return ProjectMatchDetails.Verdict.UNKNOWN;
		}
		return match.vulnerabilities().isEmpty() ? ProjectMatchDetails.Verdict.CLEAN
				: ProjectMatchDetails.Verdict.AFFECTED;
	}

	private static ProjectMatchDetails.Summary summarize(List<ProjectMatchDetails.DependencyMatch> dependencies) {
		int affected = 0;
		int clean = 0;
		int unknown = 0;
		// Distinct advisories: the same vulnerability hitting two deps counts once.
		Map<String, String> severityByVulnerability = new HashMap<>();
		for (ProjectMatchDetails.DependencyMatch dependency : dependencies) {
			switch (dependency.verdict()) {
				case AFFECTED -> affected++;
				case CLEAN -> clean++;
				case UNKNOWN -> unknown++;
			}
			for (GraphAPI.VulnerabilityRef vulnerability : dependency.vulnerabilities()) {
				severityByVulnerability.put(vulnerability.id(),
						vulnerability.severity() == null ? "NONE" : vulnerability.severity());
			}
		}
		Map<String, Long> bySeverity = new LinkedHashMap<>();
		for (String severity : SEVERITY_ORDER) {
			long count = severityByVulnerability.values().stream().filter(severity::equals).count();
			if (count > 0) {
				bySeverity.put(severity, count);
			}
		}
		return new ProjectMatchDetails.Summary(dependencies.size(), affected, clean, unknown, bySeverity);
	}

	private static int severityRank(String severity) {
		int index = SEVERITY_ORDER.indexOf(severity == null ? "NONE" : severity);
		return index < 0 ? SEVERITY_ORDER.size() : index;
	}

	/** First candidate that is non-blank after trimming; {@code null} when none is. */
	private static String firstNonBlank(String... candidates) {
		for (String candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			String trimmed = candidate.trim();
			if (!trimmed.isBlank()) {
				return trimmed;
			}
		}
		return null;
	}

	/** Result of one SBOM import. */
	public record ImportResult(Long id, String name, int dependencies, int skipped) {
	}

	/** One row in the project list. */
	public record ProjectSummary(Long id, String name, Instant createdAt, int dependencyCount) {
	}

}
