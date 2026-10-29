package io.github.pirainuthal.gstinvoice.domain;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Unit Quantity Codes (UQC) accepted by the GST portal and the e-invoice schema for goods.
 *
 * @see <a href="https://einv-apisandbox.nic.in/">NIC e-invoice API sandbox, master codes</a>
 */
public final class Uqc {

  private static final Map<String, String> CODES = new TreeMap<>();

  static {
    put("BAG", "Bags");
    put("BAL", "Bale");
    put("BDL", "Bundles");
    put("BKL", "Buckles");
    put("BOU", "Billion of units");
    put("BOX", "Box");
    put("BTL", "Bottles");
    put("BUN", "Bunches");
    put("CAN", "Cans");
    put("CBM", "Cubic meters");
    put("CCM", "Cubic centimeters");
    put("CMS", "Centimeters");
    put("CTN", "Cartons");
    put("DOZ", "Dozens");
    put("DRM", "Drums");
    put("GGK", "Great gross");
    put("GMS", "Grammes");
    put("GRS", "Gross");
    put("GYD", "Gross yards");
    put("KGS", "Kilograms");
    put("KLR", "Kilolitre");
    put("KME", "Kilometre");
    put("LTR", "Litres");
    put("MLT", "Millilitre");
    put("MTR", "Meters");
    put("MTS", "Metric ton");
    put("NOS", "Numbers");
    put("OTH", "Others");
    put("PAC", "Packs");
    put("PCS", "Pieces");
    put("PRS", "Pairs");
    put("QTL", "Quintal");
    put("ROL", "Rolls");
    put("SET", "Sets");
    put("SQF", "Square feet");
    put("SQM", "Square meters");
    put("SQY", "Square yards");
    put("TBS", "Tablets");
    put("TGM", "Ten gross");
    put("THD", "Thousands");
    put("TON", "Tonnes");
    put("TUB", "Tubes");
    put("UGS", "US gallons");
    put("UNT", "Units");
    put("YDS", "Yards");
  }

  private Uqc() {}

  private static void put(String code, String name) {
    CODES.put(code, name);
  }

  /** Whether {@code code} is a known UQC (case-sensitive, upper-case). */
  public static boolean isValid(String code) {
    return code != null && CODES.containsKey(code);
  }

  /** Human-readable description of a code. */
  public static Optional<String> describe(String code) {
    return Optional.ofNullable(code).map(CODES::get);
  }
}
