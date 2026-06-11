package dev.argusgraph.graph.application;

/** Read model: one package and how many distinct vulnerabilities affect its versions. */
public record PackageHits(String packagePurl, long vulnerabilities) {
}
