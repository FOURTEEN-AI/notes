package de.fourteen.notes.domain;

/** A request that cannot be served. The web layer maps the kind to an HTTP status. */
public class DomainException extends RuntimeException {

    public enum Kind {
        INVALID,
        NOT_FOUND,
        CONFLICT,
        /** A service the app depends on is not configured. */
        UNAVAILABLE,
        /** A service the app depends on failed. */
        UPSTREAM_FAILED
    }

    private final Kind kind;

    /** The message goes to the browser: never put a response of another service in it. */
    public DomainException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
