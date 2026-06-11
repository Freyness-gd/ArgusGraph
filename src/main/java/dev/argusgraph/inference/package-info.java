/**
 * Inference engine — derives new knowledge (currently transitive vulnerability exposure)
 * from the graph's authoritative edges by running a catalog of rules to fixpoint, writing
 * provenance-tagged derived edges.
 *
 * <p>
 * Self-contained module: depends on no other domain module. It shares the physical Neo4j
 * database with the graph module but reaches it only through its own Cypher over shared
 * node labels; it never imports graph internals. Outward contract is
 * {@link dev.argusgraph.inference.InferenceAPI}.
 */
@DomainLayer
@ApplicationModule(displayName = "Inference")
package dev.argusgraph.inference;

import org.jmolecules.architecture.layered.DomainLayer;
import org.springframework.modulith.ApplicationModule;
