package io.github.pirainuthal.gstinvoice.calc;

import java.util.List;

/** The request is well-formed but breaks a GST business rule. Mapped to HTTP 422. */
public class InvoiceRuleException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** A single violation: JSON pointer-ish field path and message. */
  public record Violation(String field, String message) {}

  private final transient List<Violation> violations;

  /** Creates the exception with a summary and the individual violations. */
  public InvoiceRuleException(String summary, List<Violation> violations) {
    super(summary);
    this.violations = List.copyOf(violations);
  }

  /** The individual rule violations. */
  public List<Violation> violations() {
    return violations;
  }
}
