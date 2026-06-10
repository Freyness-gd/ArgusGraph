package dev.argusgraph.app.api.exception;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import dev.argusgraph.shared.exception.BusinessRuleException;
import dev.argusgraph.shared.exception.ResourceNotFoundException;

/**
 * Translates application errors into RFC 9457 {@link ProblemDetail} JSON responses
 * ({@code application/problem+json}).
 *
 * <p>
 * Deliberately small: framework validation errors, one not-found mapping, and one
 * business-rule mapping. Add an {@code @ExceptionHandler} method per new failure shape —
 * there is no exception hierarchy to learn or unwind.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/** 404 — the requested aggregate/entity does not exist. */
	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		pd.setTitle("Resource Not Found");
		pd.setProperty("resource", ex.getResource());
		return pd;
	}

	/** 409 — a domain invariant / business rule was violated. */
	@ExceptionHandler(BusinessRuleException.class)
	public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		pd.setTitle("Business Rule Violation");
		return pd;
	}

	/**
	 * 400 — Jakarta Bean Validation failures on {@code @Validated} method parameters
	 * (e.g. {@code @NotBlank} on a {@code @RequestParam}). Spring raises
	 * {@link ConstraintViolationException} for these, not
	 * {@link MethodArgumentNotValidException}.
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"One or more request parameters have validation errors.");
		pd.setTitle("Validation Failed");
		return pd;
	}

	/**
	 * 400 — Jakarta Bean Validation failures on {@code @Valid}/{@code @Validated} bodies.
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, "One or more fields have validation errors.");
		pd.setTitle("Validation Failed");

		Map<String, List<String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.collect(Collectors.groupingBy(FieldError::getField,
					Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
		pd.setProperty("errors", errors);

		return ResponseEntity.status(status).body(pd);
	}

}
