package com.example.compliance.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.example.compliance.io.InputFormatException;

/** Maps failures to RFC 7807 problem details with a message the UI can show as-is. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InputFormatException.class)
    ProblemDetail invalidInput(InputFormatException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid input file", e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail missingPart(MissingServletRequestPartException e) {
        return problem(HttpStatus.BAD_REQUEST, "Missing file",
                "Both 'transactions' and 'rules' files are required (missing '" + e.getRequestPartName() + "')");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", e.getMessage());
    }

    @ExceptionHandler(ComplianceController.SampleNotFoundException.class)
    ProblemDetail sampleMissing(ComplianceController.SampleNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Sample files not found", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
