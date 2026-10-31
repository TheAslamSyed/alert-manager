package com.democorp.warroom.ingestion.api;

import com.democorp.warroom.ingestion.pipeline.SignalStep.InvalidSignalException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(InvalidSignalException.class)
    ProblemDetail invalid(InvalidSignalException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
