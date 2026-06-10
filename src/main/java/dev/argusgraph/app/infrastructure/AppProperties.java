package dev.argusgraph.app.infrastructure;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed application configuration bound from the {@code app.*} keys in
 * {@code application.yaml}. Validated at startup — a missing required value fails fast
 * instead of surfacing as a {@code null} deep in a request.
 *
 * <p>
 * Example of the pattern; add fields as your app grows. Discovered via
 * {@code @ConfigurationPropertiesScan} on {@code ArgusGraphApplication}.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

	/**
	 * Public base URL of the deployed app (no trailing slash). Used to build
	 * outward-facing links.
	 */
	@NotBlank
	private String baseUrl;

}
