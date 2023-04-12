package net.gmx.szermatt.dreamcatcher.harmony;

import static java.lang.String.format;

/** Exception thrown when Logitech authentication fails. */
class AuthFailedException extends RuntimeException {

    public AuthFailedException(String message) {
        this(message, null);
    }

    public AuthFailedException(String message, Throwable cause) {
        super(format("%s: %s", "Logitech authentication failed", message), cause);
    }
}
