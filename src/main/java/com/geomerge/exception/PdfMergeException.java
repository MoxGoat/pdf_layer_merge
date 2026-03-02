package com.geomerge.exception;

public class PdfMergeException extends RuntimeException {

    public PdfMergeException(String message) {
        super(message);
    }

    public PdfMergeException(String message, Throwable cause) {
        super(message, cause);
    }
}
