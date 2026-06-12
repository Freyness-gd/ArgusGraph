package dev.argusgraph.inference.application.strategy;

import dev.argusgraph.inference.InferenceAPI;

/** Mutable per-run accumulator. Strategies/orchestrator increment it; {@code finish()} freezes a result. */
public class RunMetrics {

	private final String engine;

	private final long startNanos;

	private int rounds;

	private long queryCount;

	private long edgesDerived;

	public RunMetrics(String engine) {
		this.engine = engine;
		this.startNanos = System.nanoTime();
	}

	public void query() {
		this.queryCount++;
	}

	public void round() {
		this.rounds++;
	}

	public void derived(long edges) {
		this.edgesDerived += edges;
	}

	public InferenceAPI.RunResult finish() {
		long durationMs = (System.nanoTime() - this.startNanos) / 1_000_000L;
		return new InferenceAPI.RunResult(this.engine, durationMs, this.rounds, this.queryCount,
				this.edgesDerived, System.currentTimeMillis());
	}

}
