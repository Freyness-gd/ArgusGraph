package dev.argusgraph.graph.application;

import java.util.List;

/** Read model: one page of packages, most-affected first. */
public record PackagePage(List<Item> items, int page, int size, long total) {

	/** One package row in the browse table. */
	public record Item(String packagePurl, String type, long versionCount, long vulnerabilityCount) {
	}

}
