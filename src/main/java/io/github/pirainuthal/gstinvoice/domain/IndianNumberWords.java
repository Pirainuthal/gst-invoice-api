package io.github.pirainuthal.gstinvoice.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Converts amounts to words using the Indian numbering system (thousand, lakh, crore).
 *
 * <p>Example: {@code 1234567.50} becomes "Rupees Twelve Lakh Thirty-Four Thousand Five Hundred
 * Sixty-Seven and Fifty Paise Only". Amounts above 99 crore keep counting in crores ("One Thousand
 * Two Hundred Crore"), which is how Indian invoices conventionally express them.
 */
public final class IndianNumberWords {

  private static final String[] UNITS = {
    "Zero",
    "One",
    "Two",
    "Three",
    "Four",
    "Five",
    "Six",
    "Seven",
    "Eight",
    "Nine",
    "Ten",
    "Eleven",
    "Twelve",
    "Thirteen",
    "Fourteen",
    "Fifteen",
    "Sixteen",
    "Seventeen",
    "Eighteen",
    "Nineteen"
  };
  private static final String[] TENS = {
    "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
  };
  private static final BigInteger CRORE = BigInteger.valueOf(10_000_000L);

  private IndianNumberWords() {}

  /**
   * Spells a rupee amount, rounding to the nearest paisa (half-up).
   *
   * @throws IllegalArgumentException for negative amounts
   */
  public static String rupees(BigDecimal amount) {
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("amount must not be negative");
    }
    BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
    BigInteger whole = scaled.toBigInteger();
    int paise = scaled.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();

    StringBuilder sb = new StringBuilder("Rupees ").append(words(whole));
    if (paise > 0) {
      sb.append(" and ").append(below100(paise)).append(" Paise");
    }
    return sb.append(" Only").toString();
  }

  /** Spells a non-negative integer in the Indian system, e.g. 100000 is "One Lakh". */
  public static String words(BigInteger n) {
    if (n.signum() == 0) {
      return UNITS[0];
    }
    BigInteger[] croreSplit = n.divideAndRemainder(CRORE);
    StringBuilder sb = new StringBuilder();
    if (croreSplit[0].signum() > 0) {
      sb.append(words(croreSplit[0])).append(" Crore");
    }
    long rest = croreSplit[1].longValueExact();
    appendGroup(sb, rest / 100_000, "Lakh");
    rest %= 100_000;
    appendGroup(sb, rest / 1_000, "Thousand");
    rest %= 1_000;
    appendGroup(sb, rest / 100, "Hundred");
    rest %= 100;
    if (rest > 0) {
      space(sb).append(below100((int) rest));
    }
    return sb.toString();
  }

  private static void appendGroup(StringBuilder sb, long value, String label) {
    if (value > 0) {
      space(sb).append(below100((int) value)).append(' ').append(label);
    }
  }

  private static StringBuilder space(StringBuilder sb) {
    return sb.isEmpty() ? sb : sb.append(' ');
  }

  private static String below100(int n) {
    if (n < 20) {
      return UNITS[n];
    }
    return n % 10 == 0 ? TENS[n / 10] : TENS[n / 10] + "-" + UNITS[n % 10];
  }
}
