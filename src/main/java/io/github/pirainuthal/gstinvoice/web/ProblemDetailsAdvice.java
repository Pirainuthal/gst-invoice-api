package io.github.pirainuthal.gstinvoice.web;

import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps errors to RFC 9457 (formerly RFC 7807) {@code application/problem+json} responses. Every
 * validation problem carries an {@code errors} array of {@code {field, message}} objects.
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final String TYPE_BASE =
      "https://github.com/Pirainuthal/gst-invoice-api/problems/";

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      @NonNull MethodArgumentNotValidException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    problem.setType(URI.create(TYPE_BASE + "validation"));
    problem.setTitle("Invalid request");
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                e ->
                    Map.of(
                        "field",
                        e.getField(),
                        "message",
                        e.getDefaultMessage() == null ? "invalid" : e.getDefaultMessage()))
            .sorted((a, b) -> a.get("field").compareTo(b.get("field")))
            .toList();
    problem.setProperty("errors", errors);
    return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      @NonNull HttpMessageNotReadableException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "The request body is not valid JSON for this endpoint (check types, enum values and"
                + " the yyyy-MM-dd date format)");
    problem.setType(URI.create(TYPE_BASE + "malformed-request"));
    problem.setTitle("Malformed request");
    return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  /** GST business-rule violations. */
  @ExceptionHandler(InvoiceRuleException.class)
  public ProblemDetail handleRule(InvoiceRuleException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    problem.setType(URI.create(TYPE_BASE + "gst-rule"));
    problem.setTitle("GST rule violation");
    problem.setProperty(
        "errors",
        ex.violations().stream()
            .map(v -> Map.of("field", v.field(), "message", v.message()))
            .toList());
    return problem;
  }
}
