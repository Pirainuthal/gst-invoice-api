package io.github.pirainuthal.gstinvoice.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GST state / union-territory codes as used in GSTINs, place of supply and the e-invoice schema.
 *
 * <p>Codes 25 (Daman and Diu, merged into 26 in 2020) and 28 (undivided Andhra Pradesh, replaced by
 * 37) are deliberately absent: the e-invoice system rejects them for new documents.
 */
public final class GstStates {

  /** How intra-state tax is levied in a jurisdiction. */
  public enum Kind {
    /** A state: CGST + SGST. */
    STATE,
    /** A union territory with its own legislature (Delhi, Puducherry, J&amp;K): CGST + SGST. */
    UT_WITH_LEGISLATURE,
    /** A union territory without legislature (and "Other Territory"): CGST + UTGST. */
    UT_WITHOUT_LEGISLATURE,
    /** Code 96: a place of supply outside India. Never a registration state. */
    OUTSIDE_INDIA
  }

  /** One jurisdiction. */
  public record State(String code, String name, Kind kind) {

    /** Label of the state-level tax component for an intra-state supply here. */
    public String stateTaxLabel() {
      return kind == Kind.UT_WITHOUT_LEGISLATURE ? "UTGST" : "SGST";
    }

    /** Whether a GST registration (GSTIN) can belong to this jurisdiction. */
    public boolean registrable() {
      return kind != Kind.OUTSIDE_INDIA;
    }
  }

  /** Place-of-supply code for supplies to a destination outside India (exports). */
  public static final String OUTSIDE_INDIA = "96";

  private static final Map<String, State> BY_CODE = new LinkedHashMap<>();

  static {
    add("01", "Jammu and Kashmir", Kind.UT_WITH_LEGISLATURE);
    add("02", "Himachal Pradesh", Kind.STATE);
    add("03", "Punjab", Kind.STATE);
    add("04", "Chandigarh", Kind.UT_WITHOUT_LEGISLATURE);
    add("05", "Uttarakhand", Kind.STATE);
    add("06", "Haryana", Kind.STATE);
    add("07", "Delhi", Kind.UT_WITH_LEGISLATURE);
    add("08", "Rajasthan", Kind.STATE);
    add("09", "Uttar Pradesh", Kind.STATE);
    add("10", "Bihar", Kind.STATE);
    add("11", "Sikkim", Kind.STATE);
    add("12", "Arunachal Pradesh", Kind.STATE);
    add("13", "Nagaland", Kind.STATE);
    add("14", "Manipur", Kind.STATE);
    add("15", "Mizoram", Kind.STATE);
    add("16", "Tripura", Kind.STATE);
    add("17", "Meghalaya", Kind.STATE);
    add("18", "Assam", Kind.STATE);
    add("19", "West Bengal", Kind.STATE);
    add("20", "Jharkhand", Kind.STATE);
    add("21", "Odisha", Kind.STATE);
    add("22", "Chhattisgarh", Kind.STATE);
    add("23", "Madhya Pradesh", Kind.STATE);
    add("24", "Gujarat", Kind.STATE);
    add("26", "Dadra and Nagar Haveli and Daman and Diu", Kind.UT_WITHOUT_LEGISLATURE);
    add("27", "Maharashtra", Kind.STATE);
    add("29", "Karnataka", Kind.STATE);
    add("30", "Goa", Kind.STATE);
    add("31", "Lakshadweep", Kind.UT_WITHOUT_LEGISLATURE);
    add("32", "Kerala", Kind.STATE);
    add("33", "Tamil Nadu", Kind.STATE);
    add("34", "Puducherry", Kind.UT_WITH_LEGISLATURE);
    add("35", "Andaman and Nicobar Islands", Kind.UT_WITHOUT_LEGISLATURE);
    add("36", "Telangana", Kind.STATE);
    add("37", "Andhra Pradesh", Kind.STATE);
    add("38", "Ladakh", Kind.UT_WITHOUT_LEGISLATURE);
    add("96", "Other Country", Kind.OUTSIDE_INDIA);
    add("97", "Other Territory", Kind.UT_WITHOUT_LEGISLATURE);
  }

  private GstStates() {}

  private static void add(String code, String name, Kind kind) {
    BY_CODE.put(code, new State(code, name, kind));
  }

  /** Looks up a two-digit code such as {@code "33"}. */
  public static Optional<State> find(String code) {
    return code == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(code));
  }

  /** All known jurisdictions in code order. */
  public static Collection<State> all() {
    return Collections.unmodifiableCollection(BY_CODE.values());
  }
}
