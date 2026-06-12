package dev.argusgraph.inference.application;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.argusgraph.shared.exception.BusinessRuleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleRegistryTest {

	// --- Minimal fakes ---

	private static InferenceRule fake(String name, int stratum) {
		return new InferenceRule() {
			@Override public String name() { return name; }
			@Override public int version() { return 1; }
			@Override public int stratum() { return stratum; }
			@Override public boolean recursive() { return false; }
			@Override public long apply(InferenceScope scope) { return 0; }
		};
	}

	private static final InferenceRule A = fake("A", 0);
	private static final InferenceRule B = fake("B", 1);
	private static final InferenceRule C = fake("C", 1);

	// --- Tests ---

	@Test
	void defaultOrderIsByStratumThenName() {
		RuleRegistry reg = new RuleRegistry(List.of(B, C, A));

		assertThat(reg.entries().stream().map(e -> e.rule().name()).toList())
				.containsExactly("A", "B", "C");
		assertThat(reg.entries()).allMatch(RuleRegistry.Entry::enabled);
	}

	@Test
	void enabledInOrderReturnsAllWhenNoneDisabled() {
		RuleRegistry reg = new RuleRegistry(List.of(B, C, A));

		assertThat(reg.enabledInOrder().stream().map(InferenceRule::name).toList())
				.containsExactly("A", "B", "C");
	}

	@Test
	void disablingRuleExcludesItFromEnabledInOrder() {
		RuleRegistry reg = new RuleRegistry(List.of(B, C, A));

		reg.setEnabled("B", false);

		assertThat(reg.enabledInOrder().stream().map(InferenceRule::name).toList())
				.containsExactly("A", "C");
		assertThat(reg.entries()).hasSize(3);
		assertThat(reg.entries().stream().filter(e -> e.rule().name().equals("B")).findFirst())
				.hasValueSatisfying(e -> assertThat(e.enabled()).isFalse());
	}

	@Test
	void reorderChangesEntryOrder() {
		RuleRegistry reg = new RuleRegistry(List.of(B, C, A));

		reg.reorder(List.of("C", "A", "B"));

		assertThat(reg.entries().stream().map(e -> e.rule().name()).toList())
				.containsExactly("C", "A", "B");
	}

	@Test
	void setEnabledForUnknownRuleThrows() {
		RuleRegistry reg = new RuleRegistry(List.of(B, C, A));

		assertThatThrownBy(() -> reg.setEnabled("X", true))
				.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void reorderWithWrongSizeThrows() {
		RuleRegistry reg = new RuleRegistry(List.of(B, C, A));

		assertThatThrownBy(() -> reg.reorder(List.of("A", "B")))
				.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void reorderPreservesDisabledFlag() {
		RuleRegistry reg = new RuleRegistry(List.of(A, B, C));

		reg.setEnabled("A", false);
		reg.reorder(List.of("C", "B", "A"));

		assertThat(
				reg.entries().stream()
						.filter(e -> e.rule().name().equals("A"))
						.findFirst()
						.orElseThrow()
						.enabled())
				.isFalse();
		assertThat(
				reg.entries().stream()
						.filter(e -> e.rule().name().equals("B"))
						.findFirst()
						.orElseThrow()
						.enabled())
				.isTrue();
		assertThat(
				reg.entries().stream()
						.filter(e -> e.rule().name().equals("C"))
						.findFirst()
						.orElseThrow()
						.enabled())
				.isTrue();
	}

	@Test
	void reorderNullThrowsBusinessRuleException() {
		RuleRegistry reg = new RuleRegistry(List.of(A, B, C));

		assertThatThrownBy(() -> reg.reorder(null))
				.isInstanceOf(BusinessRuleException.class);
	}

}
