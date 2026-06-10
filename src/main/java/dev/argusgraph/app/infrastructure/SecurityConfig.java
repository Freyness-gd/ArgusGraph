package dev.argusgraph.app.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security skeleton — OPEN BY DEFAULT for prototyping.
 *
 * <p>
 * Every request is permitted, sessions are stateless, and CSRF is disabled (there is no
 * cookie/session surface). Spring Security is on the classpath only so that locking the
 * app down later is a one-file edit rather than a dependency change.
 *
 * <p>
 * <b>To turn this into a real stateless JWT resource server:</b>
 * <ol>
 * <li>Uncomment {@code spring-boot-starter-security-oauth2-resource-server} in
 * {@code build.gradle.kts}.</li>
 * <li>Set {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} in
 * {@code application.yaml}.</li>
 * <li>Replace the {@code permitAll()} chain below with the commented JWT chain.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			// PROTOTYPE: everything is open. Tighten per-endpoint when you add auth.
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// Stateless API — no cookies, no session, no CSRF surface.
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.build();
	}

	// ── Real JWT resource server (enable as described in the class Javadoc) ───────
	//
	// @Bean
	// SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
	// return http
	// .authorizeHttpRequests(auth -> auth
	// .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
	// "/swagger-ui.html")
	// .permitAll()
	// .anyRequest().authenticated())
	// .oauth2ResourceServer(oauth2 ->
	// oauth2.jwt(org.springframework.security.config.Customizer.withDefaults()))
	// .sessionManagement(session ->
	// session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
	// .csrf(AbstractHttpConfigurer::disable)
	// .formLogin(AbstractHttpConfigurer::disable)
	// .httpBasic(AbstractHttpConfigurer::disable)
	// .build();
	// }

}
