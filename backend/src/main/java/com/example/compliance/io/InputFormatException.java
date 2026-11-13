package com.example.compliance.io;

/**
 * An input file is unusable as a whole (not valid JSON/CSV, wrong top-level shape, missing required
 * columns). Problems with individual rows/records are reported as warnings instead.
 */
public class InputFormatException extends RuntimeException {

    public InputFormatException(String message) {
        super(message);
    }

    public InputFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
