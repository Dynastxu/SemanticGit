package com.github.semanticgit.common.exception;

public class AnalysisException extends Exception {
    public AnalysisException(String message) {
        super(message);
    }

    public AnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
