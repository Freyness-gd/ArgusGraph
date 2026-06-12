package dev.argusgraph.inference.application.embedding;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.argusgraph.inference.InferenceAPI.EvalResult;
import dev.argusgraph.inference.InferenceAPI.ImputeResult;
import dev.argusgraph.inference.application.InferenceRepository;
import dev.argusgraph.inference.application.InferenceRepository.Neighbour;
import dev.argusgraph.inference.application.InferenceRepository.Prediction;

/**
 * Latent severity reasoner (r_e). Imputes a CVSS score + band for advisories lacking one from
 * the similarity-weighted CVSS of their k nearest embedded, scored neighbours; measures itself
 * by leave-one-out over the scored set.
 */
@Service
@org.jmolecules.ddd.annotation.Service
@RequiredArgsConstructor
public class SeverityImputation {

	private static final int OVERFETCH = 50;

	private static final int K = 10;

	private final InferenceRepository repository;

	@Transactional(transactionManager = "neo4jTransactionManager")
	public ImputeResult impute() {
		long start = System.nanoTime();
		List<Prediction> predictions = this.repository.imputeCandidates(OVERFETCH, K)
			.stream()
			.map(c -> {
				PredictedScore p = predict(c.neighbours());
				return new Prediction(c.vulnId(), p.cvss(), SeverityBands.of(p.cvss()), p.confidence());
			})
			.toList();
		long written = predictions.isEmpty() ? 0 : this.repository.writePredictions(predictions);
		return new ImputeResult(written, ms(start));
	}

	@Transactional(transactionManager = "neo4jTransactionManager", readOnly = true)
	public EvalResult evaluate() {
		long start = System.nanoTime();
		List<InferenceRepository.EvalCandidate> candidates = this.repository.evalCandidates(OVERFETCH, K);
		long n = candidates.size();
		long correct = 0;
		double sumAbsErr = 0;
		for (InferenceRepository.EvalCandidate c : candidates) {
			PredictedScore p = predict(c.neighbours());
			sumAbsErr += Math.abs(p.cvss() - c.actual());
			if (SeverityBands.of(p.cvss()).equals(SeverityBands.of(c.actual()))) {
				correct++;
			}
		}
		double mae = n == 0 ? 0.0 : sumAbsErr / n;
		double accuracy = n == 0 ? 0.0 : (double) correct / n;
		return new EvalResult(n, mae, accuracy, ms(start));
	}

	/** Similarity-weighted mean CVSS of the neighbours; plain mean if all weights are zero. */
	public static PredictedScore predict(List<Neighbour> neighbours) {
		double weightedSum = 0;
		double weight = 0;
		double plainSum = 0;
		for (Neighbour neighbour : neighbours) {
			weightedSum += neighbour.score() * neighbour.cvss();
			weight += neighbour.score();
			plainSum += neighbour.cvss();
		}
		int count = neighbours.size();
		double cvss = weight > 0 ? weightedSum / weight : (count > 0 ? plainSum / count : 0.0);
		double confidence = count > 0 ? weight / count : 0.0;
		return new PredictedScore(cvss, confidence);
	}

	private static long ms(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000L;
	}

	/** A predicted CVSS score with a confidence (mean neighbour similarity). */
	public record PredictedScore(double cvss, double confidence) {
	}

}
