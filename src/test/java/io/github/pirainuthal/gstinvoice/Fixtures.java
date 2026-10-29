package io.github.pirainuthal.gstinvoice;

import io.github.pirainuthal.gstinvoice.api.DocumentType;
import io.github.pirainuthal.gstinvoice.api.InvoiceRequest;
import io.github.pirainuthal.gstinvoice.api.LineItem;
import io.github.pirainuthal.gstinvoice.api.Party;
import io.github.pirainuthal.gstinvoice.api.SupplyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Fictional parties and builders shared by tests. GSTINs have valid check characters. */
public final class Fixtures {

  /** Tamil Nadu seller. */
  public static final String TN_GSTIN = "33ABCDE1234F1Z7";

  /** Karnataka buyer. */
  public static final String KA_GSTIN = "29PQRSX5678K1ZU";

  /** Second Tamil Nadu registration (e.g. an SEZ unit). */
  public static final String TN_GSTIN_2 = "33ABCDE1234F2Z6";

  /** Chandigarh (UT without legislature). */
  public static final String CH_GSTIN = "04AAAAA1111A1Z4";

  public static final LocalDate DATE = LocalDate.of(2026, 10, 29);

  private Fixtures() {}

  public static Party party(String name, String gstin, String state) {
    return new Party(
        name, null, gstin, state, "12, Mill Road", null, "Coimbatore", "641004", null, null);
  }

  public static Party seller() {
    return party("Kaveri Loom Works Private Limited", TN_GSTIN, "33");
  }

  public static Party buyerKa() {
    return new Party(
        "Deccan Retail LLP",
        null,
        KA_GSTIN,
        "29",
        "4th Cross, Indiranagar",
        null,
        "Bengaluru",
        "560038",
        null,
        null);
  }

  public static LineItem item(String qty, String price, String rate) {
    return item("Cotton shirt", "6205", qty, "PCS", price, null, rate);
  }

  public static LineItem item(
      String desc, String hsn, String qty, String unit, String price, String disc, String rate) {
    return new LineItem(
        desc,
        hsn,
        new BigDecimal(qty),
        unit,
        new BigDecimal(price),
        disc == null ? null : new BigDecimal(disc),
        new BigDecimal(rate),
        null,
        null);
  }

  public static InvoiceRequest request(Party seller, Party buyer, List<LineItem> items) {
    return request(seller, buyer, null, null, items);
  }

  public static InvoiceRequest request(
      Party seller, Party buyer, SupplyType supplyType, String pos, List<LineItem> items) {
    return new InvoiceRequest(
        "KLW/26-27/0042", DATE, DocumentType.INV, supplyType, seller, buyer, pos, items, null);
  }
}
