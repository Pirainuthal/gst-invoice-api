package io.github.pirainuthal.gstinvoice.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Structural and checksum validation of a 15-character GSTIN.
 *
 * <p>Layout: {@code SS PPPPPPPPPP E Z C} &mdash; 2-digit state code, 10-character PAN, entity
 * number (1-9, A-Z), a 14th character ({@code Z} for regular taxpayers) and a mod-36 check
 * character computed over the first 14 characters.
 *
 * <p>Only the PAN-based format of regular taxpayers is accepted. Special registrations (TDS/TCS
 * deductors keyed on TAN, UN bodies, non-resident OIDAR providers) use other layouts and are out of
 * scope.
 */
public final class Gstin {

  private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final Pattern FORMAT =
      Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z][A-Z0-9][0-9A-Z]$");

  private Gstin() {}

  /**
   * Computes the GSTIN check character for the first 14 characters.
   *
   * <p>Each character is mapped to its index in {@code 0-9A-Z}; odd positions (1-based) are
   * weighted 1 and even positions 2; each product contributes {@code quotient + remainder} when
   * divided by 36; the check value is {@code (36 - sum mod 36) mod 36}.
   *
   * @throws IllegalArgumentException if the input is not 14 characters from {@code 0-9A-Z}
   */
  public static char checksum(String first14) {
    if (first14 == null || first14.length() != 14) {
      throw new IllegalArgumentException("expected 14 characters");
    }
    int sum = 0;
    for (int i = 0; i < 14; i++) {
      int value = CHARSET.indexOf(first14.charAt(i));
      if (value < 0) {
        throw new IllegalArgumentException("invalid character: " + first14.charAt(i));
      }
      int product = value * (i % 2 == 0 ? 1 : 2);
      sum += product / 36 + product % 36;
    }
    return CHARSET.charAt((36 - sum % 36) % 36);
  }

  /** Normalises user input: trims and upper-cases. Returns {@code null} for {@code null}. */
  public static String normalise(String gstin) {
    return gstin == null ? null : gstin.trim().toUpperCase(Locale.ROOT);
  }

  /**
   * Returns human-readable problems with the GSTIN; an empty list means it is valid.
   *
   * <p>The input is checked as given (callers should {@link #normalise} first if they accept
   * lower-case input).
   */
  public static List<String> problems(String gstin) {
    List<String> problems = new ArrayList<>();
    if (gstin == null || gstin.isBlank()) {
      problems.add("GSTIN is empty");
      return problems;
    }
    if (gstin.length() != 15) {
      problems.add("GSTIN must be exactly 15 characters, got " + gstin.length());
      return problems;
    }
    if (!FORMAT.matcher(gstin).matches()) {
      problems.add(
          "GSTIN must match NN AAAAA9999A E Z C (state code, PAN, entity number, 'Z', checksum)");
      return problems;
    }
    String state = gstin.substring(0, 2);
    if (GstStates.find(state).filter(GstStates.State::registrable).isEmpty()) {
      problems.add("GSTIN state code '" + state + "' is not a valid GST state code");
    }
    char expected = checksum(gstin.substring(0, 14));
    if (gstin.charAt(14) != expected) {
      problems.add(
          "GSTIN checksum mismatch: last character should be '"
              + expected
              + "' but is '"
              + gstin.charAt(14)
              + "'");
    }
    return problems;
  }

  /** Whether {@code gstin} is structurally valid with a correct check character. */
  public static boolean isValid(String gstin) {
    return problems(gstin).isEmpty();
  }

  /** The two-digit state code prefix. Assumes a structurally valid GSTIN. */
  public static String stateCode(String gstin) {
    return gstin.substring(0, 2);
  }

  /** The embedded PAN. Assumes a structurally valid GSTIN. */
  public static String pan(String gstin) {
    return gstin.substring(2, 12);
  }
}
