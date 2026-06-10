package dev.argusgraph.shared.exception;

/**
 * Thrown by an aggregate or service when a domain invariant / business rule is violated
 * (e.g. ordering more than the available stock). Mapped to HTTP 409 Conflict by
 * {@code GlobalExceptionHandler}.
 *
 * <p>
 * Keep these messages safe to surface to API clients — they appear verbatim in the
 * ProblemDetail {@code detail} field.
 */
public class BusinessRuleException extends RuntimeException {

	public BusinessRuleException(String message) {
		super(message);
	}

}
