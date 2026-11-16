package com.reverse.nsu.exception;

import org.springframework.http.HttpStatus;

public class InterviewRequestException extends IllegalArgumentException {

    private final HttpStatus status;

    public InterviewRequestException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
