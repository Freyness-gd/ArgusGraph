package dev.argusgraph.app.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger-UI metadata. Swagger UI is served at {@code /swagger-ui.html} and the
 * spec at {@code /v3/api-docs} (both under the {@code /api/v1} context path).
 *
 * <p>
 * A {@code bearerAuth} scheme is pre-declared so that, once you enable JWT security in
 * {@link dev.argusgraph.app.infrastructure.SecurityConfig}, the Swagger "Authorize"
 * button works with no further change. It is intentionally NOT a global requirement while
 * the prototype runs open.
 */
@Configuration
public class OpenApiConfig {

	public static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI argusGraphOpenAPI() {
		return new OpenAPI()
			.info(new Info().title("ArgusGraph API")
				.version("v1")
				.description("CVE knowledge graph for software supply chain analysis — "
						+ "typed ingestion (ingest module) and graph reads (graph module)."))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
		// When auth is enabled, also: .addSecurityItem(new
		// SecurityRequirement().addList(BEARER_SCHEME))
	}

}
