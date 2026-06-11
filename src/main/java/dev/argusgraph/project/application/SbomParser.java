package dev.argusgraph.project.application;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import dev.argusgraph.graph.Purl;
import dev.argusgraph.shared.exception.BusinessRuleException;

/**
 * Minimal CycloneDX reader: pulls {@code components[].purl}, canonicalises each through
 * {@link Purl}, and counts everything unusable (no purl, unparseable, versionless,
 * duplicate) as skipped. No CycloneDX library — the three fields we need don't justify
 * the dependency.
 */
@Service
@org.jmolecules.ddd.annotation.Service
public class SbomParser {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public ParsedSbom parse(String sbomJson) {
		JsonNode root;
		try {
			root = MAPPER.readTree(sbomJson);
		}
		catch (JacksonException ex) {
			throw new BusinessRuleException("Not a CycloneDX SBOM: body is not valid JSON.");
		}
		JsonNode components = root.path("components");
		if (!components.isArray()) {
			throw new BusinessRuleException("Not a CycloneDX SBOM: missing components array.");
		}
		Set<String> purls = new LinkedHashSet<>();
		Map<String, String> refToPurl = new HashMap<>();
		int skipped = 0;
		for (JsonNode component : components) {
			String raw = component.path("purl").asString(null);
			if (raw == null || raw.isBlank()) {
				skipped++;
				continue;
			}
			try {
				Purl parsed = Purl.parse(raw);
				if (!parsed.hasVersion()) {
					skipped++;
					continue;
				}
				String key = parsed.versionKey();
				// bom-ref maps to this purl; tools that omit bom-ref use the purl as the ref.
				String ref = component.path("bom-ref").asString(null);
				refToPurl.put(ref != null && !ref.isBlank() ? ref : raw, key);
				refToPurl.put(key, key);
				if (!purls.add(key)) {
					skipped++;
				}
			}
			catch (RuntimeException ex) {
				skipped++;
			}
		}
		Set<DependencyEdge> edges = new LinkedHashSet<>();
		for (JsonNode dependency : root.path("dependencies")) {
			String from = refToPurl.get(dependency.path("ref").asString(""));
			if (from == null) {
				continue;
			}
			for (JsonNode dependsOn : dependency.path("dependsOn")) {
				String to = refToPurl.get(dependsOn.asString(""));
				if (to != null && !to.equals(from)) {
					edges.add(new DependencyEdge(from, to));
				}
			}
		}
		String defaultName = root.path("metadata").path("component").path("name").asString(null);
		return new ParsedSbom(defaultName, purls, edges, skipped);
	}

	/** Outcome of one SBOM parse: optional default name, canonical purls, dependency edges, skipped count. */
	public record ParsedSbom(String defaultName, Set<String> purls, Set<DependencyEdge> edges, int skipped) {
	}

	/** One {@code DEPENDS_ON} edge between two canonical version-level purls. */
	public record DependencyEdge(String fromPurl, String toPurl) {
	}

}
