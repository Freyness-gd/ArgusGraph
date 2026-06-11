package dev.argusgraph.ingest.worker.application;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory record of source-fetch jobs for the dashboard: the last {@code 20} jobs,
 * newest first. Deliberately not persistent — a restart starts a fresh history (the
 * durable queues keep their depth regardless). Thread safety: structural changes and
 * snapshots synchronize on the deque; per-job fields are atomic/volatile so the single
 * fetch thread can update them lock-free while status requests read.
 */
public class IngestJobRegistry {

	private static final int MAX_JOBS = 20;

	private final AtomicLong sequence = new AtomicLong();

	private final Deque<JobRecord> jobs = new ArrayDeque<>();

	/** Register a new RUNNING job, trimming the history to the newest {@code MAX_JOBS}. */
	public JobRecord start(String ecosystem) {
		JobRecord record = new JobRecord(this.sequence.incrementAndGet(), ecosystem, Instant.now());
		synchronized (this.jobs) {
			this.jobs.addFirst(record);
			while (this.jobs.size() > MAX_JOBS) {
				this.jobs.removeLast();
			}
		}
		return record;
	}

	/** Immutable view of every tracked job, newest first. */
	public List<JobView> snapshot() {
		synchronized (this.jobs) {
			return this.jobs.stream().map(JobRecord::view).toList();
		}
	}

	/** Job lifecycle states. */
	public enum State {

		RUNNING, COMPLETED, FAILED

	}

	/** Mutable handle owned by the running fetch; readers only ever see {@link JobView} copies. */
	public static final class JobRecord {

		private final long id;

		private final String ecosystem;

		private final Instant startedAt;

		private final AtomicInteger documentsPublished = new AtomicInteger();

		private volatile State state = State.RUNNING;

		private volatile Instant finishedAt;

		private volatile String error;

		private JobRecord(long id, String ecosystem, Instant startedAt) {
			this.id = id;
			this.ecosystem = ecosystem;
			this.startedAt = startedAt;
		}

		public void incrementPublished() {
			this.documentsPublished.incrementAndGet();
		}

		public int documentsPublished() {
			return this.documentsPublished.get();
		}

		public void complete() {
			this.state = State.COMPLETED;
			this.finishedAt = Instant.now();
		}

		public void fail(String error) {
			this.state = State.FAILED;
			this.error = error;
			this.finishedAt = Instant.now();
		}

		private JobView view() {
			return new JobView(this.id, this.ecosystem, this.state, this.documentsPublished.get(), this.startedAt,
					this.finishedAt, this.error);
		}

	}

	/** Immutable per-job view handed to the status endpoint. */
	public record JobView(long id, String ecosystem, State state, int documentsPublished, Instant startedAt,
			Instant finishedAt, String error) {
	}

}
