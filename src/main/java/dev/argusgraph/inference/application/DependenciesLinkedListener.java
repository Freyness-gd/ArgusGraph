package dev.argusgraph.inference.application;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import dev.argusgraph.inference.InferenceAPI;

/** Runs R1 for the freshly linked dependency subgraph once an import has committed. */
@Component
@RequiredArgsConstructor
class DependenciesLinkedListener {

	private final InferenceAPI inference;

	@ApplicationModuleListener
	void on(InferenceAPI.DependenciesLinked event) {
		this.inference.runFor(event.purls());
	}

}
