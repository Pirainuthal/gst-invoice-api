package io.github.pirainuthal.gstinvoice.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Formats amounts with Indian digit grouping: {@code 1234567.5} becomes {@code 12,34,567.50}. */
public final class IndianNumberFormat {

  private IndianNumberFormat() {}

  /** Formats with exactly {@code scale} decimals (half-up) and lakh/crore grouping. */
  public static String format(BigDecimal value, int scale) {
    BigDecimal scaled = value.setScale(scale, RoundingMode.HALF_UP);
    String plain = scaled.abs().toPlainString();
    int dot = plain.indexOf('.');
    String intPart = dot < 0 ? plain : plain.substring(0, dot);
    String fraction = dot < 0 ? "" : plain.substring(dot);

    StringBuilder grouped = new StringBuilder();
    int len = intPart.length();
    if (len <= 3) {
      grouped.append(intPart);
    } else {
      String head = intPart.substring(0, len - 3);
      String tail = intPart.substring(len - 3);
      int first = head.length() % 2;
      if (first > 0) {
        grouped.append(head, 0, first);
      }
      for (int i = first; i < head.length(); i += 2) {
        if (!grouped.isEmpty()) {
          grouped.append(',');
        }
        grouped.append(head, i, i + 2);
      }
      grouped.append(',').append(tail);
    }
    return (scaled.signum() < 0 ? "-" : "") + grouped + fraction;
  }

  /** Two-decimal amount formatting. */
  public static String amount(BigDecimal value) {
    return format(value, 2);
  }
}
