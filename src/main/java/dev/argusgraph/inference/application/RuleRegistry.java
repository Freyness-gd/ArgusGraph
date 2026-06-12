package dev.argusgraph.inference.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.argusgraph.shared.exception.BusinessRuleException;

/**
 * Ordered, runtime-mutable catalog of the engine's inference rules. Seeded from the rule beans
 * (default order: stratum ascending, then name) and editable at runtime — rules can be enabled,
 * disabled, and reordered. The engine runs the enabled rules in this list's order. In-memory:
 * edits last for the JVM lifetime and reset to the default order on restart.
 */
@Component
@org.jmolecules.ddd.annotation.Service
public class RuleRegistry {

	private final List<Entry> entries = new ArrayList<>();

	public RuleRegistry(List<InferenceRule> rules) {
		List<InferenceRule> ordered = new ArrayList<>(rules);
		ordered.sort(Comparator.comparingInt(InferenceRule::stratum).thenComparing(InferenceRule::name));
		for (InferenceRule rule : ordered) {
			this.entries.add(new Entry(rule, true));
		}
	}

	/** All rules in current order (enabled and disabled). */
	public synchronized List<Entry> entries() {
		return List.copyOf(this.entries);
	}

	/** Enabled rules only, in current order — what the engine runs. */
	public synchronized List<InferenceRule> enabledInOrder() {
		return this.entries.stream().filter(Entry::enabled).map(Entry::rule).toList();
	}

	/** Enable or disable one rule by name. */
	public synchronized void setEnabled(String name, boolean enabled) {
		for (int i = 0; i < this.entries.size(); i++) {
			if (this.entries.get(i).rule().name().equals(name)) {
				this.entries.set(i, new Entry(this.entries.get(i).rule(), enabled));
				return;
			}
		}
		throw new BusinessRuleException("Unknown rule: " + name);
	}

	/**
	 * Reorder the whole catalog. {@code orderedNames} must be a permutation of the current rule names.
	 * Order is user-controlled and not validated against rule strata — placing a dependent rule before
	 * its prerequisite (e.g. R1 before R2) will change the derived result.
	 */
	public synchronized void reorder(List<String> orderedNames) {
		Map<String, Entry> byName = new LinkedHashMap<>();
		for (Entry e : this.entries) {
			byName.put(e.rule().name(), e);
		}
		if (orderedNames == null || orderedNames.size() != byName.size()
				|| !byName.keySet().equals(new HashSet<>(orderedNames))) {
			throw new BusinessRuleException(
					"Order must be a permutation of " + byName.keySet() + " (got " + orderedNames + ")");
		}
		List<Entry> reordered = new ArrayList<>();
		for (String name : orderedNames) {
			reordered.add(byName.get(name));
		}
		this.entries.clear();
		this.entries.addAll(reordered);
	}

	/** One catalog entry: a rule plus whether the engine currently runs it. */
	public record Entry(InferenceRule rule, boolean enabled) {
	}

}
