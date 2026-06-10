/**
 * Ingest module — input adapters that feed the knowledge graph.
 *
 * <p>
 * Today this is a typed REST API; the planned scraper workers (OSV, NVD, GitHub Advisory
 * Database, deps.dev) will live here as additional adapters mapping source data onto the
 * same calls. The module talks to the graph module ONLY through its published
 * {@link dev.argusgraph.graph.GraphAPI} contract.
 */
@ApplicationLayer
@ApplicationModule(displayName = "Ingest")
package dev.argusgraph.ingest;

import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.modulith.ApplicationModule;
