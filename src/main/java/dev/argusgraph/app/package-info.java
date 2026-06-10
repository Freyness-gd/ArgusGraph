/**
 * Cross-cutting bootstrap and infrastructure: HTTP error handling, security, OpenAPI, and
 * application-wide configuration properties. Not a domain module — it wires the
 * application together and is allowed to be referenced by the framework.
 */
@org.springframework.modulith.ApplicationModule(displayName = "App / Cross-cutting")
package dev.argusgraph.app;
