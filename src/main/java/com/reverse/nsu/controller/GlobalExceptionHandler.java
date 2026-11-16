package com.reverse.nsu.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.reverse.nsu.exception.InvalidRecruitmentRequestException;
import com.reverse.nsu.exception.InterviewRequestException;
import com.reverse.nsu.exception.InterviewSlotConflictException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InterviewRequestException.class)
    public ResponseEntity<Map<String, String>> handleInterviewRequest(InterviewRequestException e) {
        return ResponseEntity.status(e.getStatus())
                .body(Map.of("status", "error", "message", e.getMessage()));
    }

    @ExceptionHandler(InterviewSlotConflictException.class)
    public ResponseEntity<Map<String, String>> handleInterviewConflict(InterviewSlotConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", "error", "message", e.getMessage()));
    }

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessException.class})
    public ResponseEntity<Map<String, String>> handleDbException(Exception e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "데이터베이스 연결에 실패했습니다. 잠시 후 다시 시도해주세요."));
    }

    @ExceptionHandler(InvalidRecruitmentRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRecruitmentRequest(
            InvalidRecruitmentRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", e.getMessage()));
    }
}
