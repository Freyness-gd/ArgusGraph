/**
 * Knowledge-graph core — packages, package versions, vulnerabilities, and the
 * relationships between them, persisted in Neo4j.
 *
 * <p>
 * Self-contained module: it depends on no other domain module. Its outward contract is
 * {@link dev.argusgraph.graph.GraphAPI} (plus the {@link dev.argusgraph.graph.Purl} value
 * object); everything under {@code api}, {@code application}, and {@code infrastructure}
 * is module-private.
 */
@DomainLayer
@ApplicationModule(displayName = "Graph")
package dev.argusgraph.graph;

import org.jmolecules.architecture.layered.DomainLayer;
import org.springframework.modulith.ApplicationModule;
