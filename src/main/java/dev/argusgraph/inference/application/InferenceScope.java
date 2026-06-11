package dev.argusgraph.inference.application;

import java.util.Set;

/**
 * What a rule run covers: either the whole graph or a bounded set of source package-version
 * purls. {@code sourcePurls() == null} means "all".
 */
public record InferenceScope(Set<String> sourcePurls) {

    public static InferenceScope all() {
        return new InferenceScope(null);
    }

    public static InferenceScope of(Set<String> sourcePurls) {
        return new InferenceScope(sourcePurls);
    }

    public boolean isAll() {
        return this.sourcePurls == null;
    }

}
