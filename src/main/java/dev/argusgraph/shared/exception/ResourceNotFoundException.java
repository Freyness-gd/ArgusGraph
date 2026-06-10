package dev.argusgraph.shared.exception;

import lombok.Getter;

/**
 * Thrown when a requested aggregate/entity does not exist. Mapped to HTTP 404 by
 * {@code GlobalExceptionHandler}.
 *
 * <p>
 * Carries the domain type and the looked-up identifier so the error response can name
 * what was missing without each module defining its own not-found exception.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

	private final String resource;

	private final transient Object identifier;

	public ResourceNotFoundException(Class<?> resourceType, Object identifier) {
		this(resourceType.getSimpleName(), identifier);
	}

	/**
	 * Name the resource as a plain string — useful across module boundaries, where the
	 * caller must not import the owning module's aggregate type.
	 */
	public ResourceNotFoundException(String resource, Object identifier) {
		super("%s with id %s was not found.".formatted(resource, identifier));
		this.resource = resource;
		this.identifier = identifier;
	}

}
