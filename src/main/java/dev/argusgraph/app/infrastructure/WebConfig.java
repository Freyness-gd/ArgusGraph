package dev.argusgraph.app.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Prefixes every controller in this codebase with {@code /api/v1} so the dashboard SPA
 * can be served from the server root. Replaces the old {@code server.servlet.context-path},
 * which also dragged static resources, the actuator, and Swagger UI under {@code /api/v1}.
 * Scoped by base package on purpose: springdoc's and the actuator's own controllers must
 * stay where their defaults put them. Note: any future MVC controller under
 * {@code dev.argusgraph} (e.g. a SPA deep-link forwarder) would also get the prefix —
 * serve the SPA via static resources instead, or tighten the predicate.
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix("/api/v1", HandlerTypePredicate.forBasePackage("dev.argusgraph"));
	}

}
