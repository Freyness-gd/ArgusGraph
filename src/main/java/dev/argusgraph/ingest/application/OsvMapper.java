package dev.argusgraph.ingest.application;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import dev.argusgraph.graph.GraphAPI;
import dev.argusgraph.graph.Purl;
import dev.argusgraph.ingest.api.OsvVulnerabilityRequest;

/**
 * Maps OSV documents onto {@link GraphAPI} input: vulnerability fields one-to-one,
 * package identity to a canonical purl. Prefers the document's own {@code purl}; when
 * absent, builds one from ecosystem+name (handling Maven {@code group:artifact}, Go
 * module paths, npm scopes, and OSV release suffixes like {@code Debian:11}). Unknown
 * ecosystems fall back to {@code pkg:generic/<ecosystem>/<name>} so no advisory is lost.
 */
@Component
@RequiredArgsConstructor
public class OsvMapper {

	/** OSV ecosystem (lower-cased, release suffix stripped) → purl type. */
	private static final Map<String, String> ECOSYSTEM_TO_PURL_TYPE = Map.ofEntries(Map.entry("npm", "npm"),
			Map.entry("pypi", "pypi"), Map.entry("maven", "maven"), Map.entry("go", "golang"),
			Map.entry("crates.io", "cargo"), Map.entry("rubygems", "gem"), Map.entry("packagist", "composer"),
			Map.entry("nuget", "nuget"), Map.entry("hex", "hex"), Map.entry("pub", "pub"),
			Map.entry("hackage", "hackage"), Map.entry("debian", "deb"), Map.entry("alpine", "apk"));

	private final ObjectMapper objectMapper;

	public GraphAPI.VulnerabilityInput toInput(OsvVulnerabilityRequest doc) {
		List<GraphAPI.SeverityInput> severities = doc.severity() == null ? List.of()
				: doc.severity().stream().map(s -> new GraphAPI.SeverityInput(s.type(), s.score())).toList();
		List<GraphAPI.ReferenceInput> references = doc.references() == null ? List.of()
				: doc.references().stream().map(r -> new GraphAPI.ReferenceInput(r.type(), r.url())).toList();
		return new GraphAPI.VulnerabilityInput(doc.id(), doc.modified(), doc.published(), doc.withdrawn(),
				doc.summary(), doc.details(), doc.aliases(), doc.related(), doc.upstream(), severities, references);
	}

	/**
	 * Versionless package identity for an OSV {@code affected.package}; null when the
	 * entry carries neither a purl nor an ecosystem+name pair.
	 */
	public Purl toPackagePurl(OsvVulnerabilityRequest.OsvPackage pkg) {
		if (pkg == null) {
			return null;
		}
		if (pkg.purl() != null && !pkg.purl().isBlank()) {
			return Purl.parse(pkg.purl());
		}
		if (pkg.name() == null || pkg.name().isBlank() || pkg.ecosystem() == null || pkg.ecosystem().isBlank()) {
			return null;
		}
		// OSV ecosystems may carry a release suffix, e.g. "Debian:11" — the prefix names it.
		String ecosystem = pkg.ecosystem().split(":", 2)[0].trim().toLowerCase(Locale.ROOT);
		String type = ECOSYSTEM_TO_PURL_TYPE.get(ecosystem);
		String name = pkg.name().trim();
		if (type == null) {
			return Purl.of("generic", ecosystem, name, null);
		}
		return switch (type) {
			// Maven names are "group:artifact" — group becomes the purl namespace.
			case "maven" -> {
				String[] parts = name.split(":", 2);
				yield parts.length == 2 ? Purl.of(type, parts[0], parts[1], null) : Purl.of(type, null, name, null);
			}
			// Go names are module paths — everything before the last segment is the namespace.
			case "golang" -> {
				int lastSlash = name.lastIndexOf('/');
				yield lastSlash > 0
						? Purl.of(type, name.substring(0, lastSlash), name.substring(lastSlash + 1), null)
						: Purl.of(type, null, name, null);
			}
			// Scoped npm packages are "@scope/name" — the scope is the namespace.
			case "npm" -> {
				int slash = name.indexOf('/');
				yield name.startsWith("@") && slash > 0
						? Purl.of(type, name.substring(0, slash), name.substring(slash + 1), null)
						: Purl.of(type, null, name, null);
			}
			default -> Purl.of(type, null, name, null);
		};
	}

	/** Raw OSV ranges, preserved verbatim as JSON for the future inference engine. */
	public String rangesJson(List<OsvVulnerabilityRequest.OsvRange> ranges) {
		try {
			return this.objectMapper.writeValueAsString(ranges);
		}
		catch (JacksonException ex) {
			throw new IllegalStateException("Could not serialise OSV ranges.", ex);
		}
	}

}
