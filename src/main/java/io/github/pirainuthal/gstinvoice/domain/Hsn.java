package io.github.pirainuthal.gstinvoice.domain;

import java.util.regex.Pattern;

/** HSN (goods) and SAC (services) code helpers. */
public final class Hsn {

  /** 4, 6 or 8 digits, as accepted on tax invoices and by the e-invoice schema. */
  public static final String PATTERN = "^(\\d{4}|\\d{6}|\\d{8})$";

  private static final Pattern COMPILED = Pattern.compile(PATTERN);

  private Hsn() {}

  /** Whether the code has a valid length and only digits. */
  public static boolean isValid(String code) {
    return code != null && COMPILED.matcher(code).matches();
  }

  /** Services Accounting Codes all sit in chapter 99 of the scheme of classification. */
  public static boolean isService(String code) {
    return code != null && code.startsWith("99");
  }
}
