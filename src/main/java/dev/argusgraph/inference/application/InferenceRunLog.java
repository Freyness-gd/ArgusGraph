package dev.argusgraph.inference.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.InferenceAPI.RunResult;

/** Bounded in-memory log of recent recompute runs — summary numbers only, so it cannot grow. */
@Component
public class InferenceRunLog {

	private static final int CAPACITY = 50;

	private final Deque<RunResult> runs = new ArrayDeque<>();

	public synchronized void record(RunResult run) {
		this.runs.addFirst(run);
		while (this.runs.size() > CAPACITY) {
			this.runs.removeLast();
		}
	}

	public synchronized List<RunResult> recent() {
		return new ArrayList<>(this.runs);
	}

}
