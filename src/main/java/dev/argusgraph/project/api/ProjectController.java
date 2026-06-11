package dev.argusgraph.project.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.argusgraph.project.application.ProjectService;

/**
 * Imported projects: CycloneDX SBOM in, on-demand vulnerability match out. Projects
 * live in embedded H2 — never in the graph.
 */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Imported SBOM projects matched against the knowledge graph.")
public class ProjectController {

	private final ProjectService projects;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Import a CycloneDX SBOM as a new project")
	public ProjectImportResponse importProject(@RequestParam(required = false) String name,
			@RequestBody String sbomJson) {
		return ProjectImportResponse.from(this.projects.importSbom(name, sbomJson));
	}

	@GetMapping
	@Operation(summary = "List imported projects, newest first")
	public List<ProjectSummaryResponse> list() {
		return this.projects.list().stream().map(ProjectSummaryResponse::from).toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "A project with its live vulnerability match (AFFECTED first)")
	public ProjectDetailResponse get(@PathVariable long id) {
		return ProjectDetailResponse.from(this.projects.get(id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Delete an imported project")
	public void delete(@PathVariable long id) {
		this.projects.delete(id);
	}

}
