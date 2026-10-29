package io.github.pirainuthal.gstinvoice.einvoice;

import io.github.pirainuthal.gstinvoice.api.Party;
import io.github.pirainuthal.gstinvoice.api.SupplyType;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.LineCalculation;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException.Violation;
import io.github.pirainuthal.gstinvoice.domain.GstStates;
import io.github.pirainuthal.gstinvoice.domain.Uqc;
import io.github.pirainuthal.gstinvoice.einvoice.EInvoice.BuyerDtls;
import io.github.pirainuthal.gstinvoice.einvoice.EInvoice.DocDtls;
import io.github.pirainuthal.gstinvoice.einvoice.EInvoice.Item;
import io.github.pirainuthal.gstinvoice.einvoice.EInvoice.SellerDtls;
import io.github.pirainuthal.gstinvoice.einvoice.EInvoice.TranDtls;
import io.github.pirainuthal.gstinvoice.einvoice.EInvoice.ValDtls;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Maps a calculated invoice to the e-invoice schema 1.1 JSON payload.
 *
 * <p>This produces the request body only. It does not authenticate with, or submit to, the Invoice
 * Registration Portal, so no IRN, acknowledgement or signed QR code is produced.
 */
@Component
public class EInvoiceMapper {

  /** Schema version required by the IRP. */
  public static final String SCHEMA_VERSION = "1.1";

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
  private static final Pattern DOC_NO = Pattern.compile("^[1-9A-Za-z][A-Za-z0-9/-]{0,15}$");
  private static final String UNREGISTERED = "URP";
  private static final String EXPORT_PIN = "999999";
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private static final Pattern EMAIL = Pattern.compile("^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$");
  private static final int MIN_TEXT = 3;

  /**
   * Builds the payload, first checking the extra fields the IRP requires.
   *
   * @throws InvoiceRuleException when the invoice cannot be reported as an e-invoice
   */
  public EInvoice map(InvoiceCalculation inv) {
    validate(inv);
    SupplyType supplyType = inv.supplyType();
    boolean export = supplyType.export();
    Party s = inv.seller();
    Party b = inv.buyer();

    TranDtls tran =
        new TranDtls(
            "GST",
            supplyType == SupplyType.REGULAR ? "B2B" : supplyType.eInvoiceCode(),
            "N",
            null,
            "N");
    DocDtls doc =
        new DocDtls(inv.documentType().name(), inv.invoiceNumber(), DATE.format(inv.invoiceDate()));
    SellerDtls seller =
        new SellerDtls(
            s.gstin(),
            s.legalName(),
            s.tradeName(),
            s.address1(),
            s.address2(),
            s.location(),
            Integer.valueOf(s.pincode()),
            s.stateCode(),
            s.phone(),
            s.email());
    BuyerDtls buyer =
        new BuyerDtls(
            export ? UNREGISTERED : b.gstin(),
            b.legalName(),
            b.tradeName(),
            inv.placeOfSupply().code(),
            b.address1(),
            b.address2(),
            b.location(),
            Integer.valueOf(export ? EXPORT_PIN : b.pincode()),
            export ? GstStates.OUTSIDE_INDIA : b.stateCode(),
            b.phone(),
            b.email());

    List<Item> items = inv.lines().stream().map(EInvoiceMapper::item).toList();
    InvoiceCalculation.Totals t = inv.totals();
    ValDtls val =
        new ValDtls(
            t.taxableValue(),
            t.cgstAmount(),
            t.sgstAmount(),
            t.igstAmount(),
            t.cessAmount(),
            ZERO,
            ZERO,
            ZERO,
            t.roundOff(),
            t.grandTotal());
    return new EInvoice(SCHEMA_VERSION, tran, doc, seller, buyer, items, val);
  }

  private static Item item(LineCalculation l) {
    return new Item(
        String.valueOf(l.lineNo()),
        l.description(),
        l.service() ? "Y" : "N",
        l.hsnCode(),
        l.quantity(),
        l.unit(),
        l.unitPrice(),
        l.grossAmount(),
        l.discountAmount(),
        l.taxableValue(),
        l.gstRate(),
        l.igstAmount(),
        l.cgstAmount(),
        l.sgstAmount(),
        l.cessRate(),
        l.cessAmount(),
        l.cessNonAdvolAmount(),
        BigDecimal.ZERO,
        ZERO,
        ZERO,
        ZERO,
        l.lineTotal());
  }

  private static void validate(InvoiceCalculation inv) {
    List<Violation> v = new ArrayList<>();
    boolean export = inv.supplyType().export();
    Party s = inv.seller();
    Party b = inv.buyer();

    if (!DOC_NO.matcher(inv.invoiceNumber()).matches()) {
      v.add(
          new Violation(
              "invoiceNumber", "e-invoice document numbers must not start with '0', '/' or '-'"));
    }
    if (b.gstin() == null && !export) {
      v.add(
          new Violation(
              "buyer.gstin",
              "e-invoicing covers B2B, SEZ and export supplies; B2C invoices are not reported to"
                  + " the IRP"));
    }
    if (b.gstin() != null && b.gstin().equals(s.gstin())) {
      v.add(new Violation("buyer.gstin", "must differ from the supplier's GSTIN"));
    }
    requireAddress(v, "seller", s, true);
    requireAddress(v, "buyer", b, !export);
    checkPartyText(v, "seller", s);
    checkPartyText(v, "buyer", b);
    for (LineCalculation l : inv.lines()) {
      String path = "items[" + (l.lineNo() - 1) + "]";
      text(v, path + ".description", l.description());
      if (!l.service() && !Uqc.isValid(l.unit())) {
        v.add(
            new Violation(
                path + ".unit",
                "'" + l.unit() + "' is not a GST Unit Quantity Code (e.g. NOS, PCS, KGS)"));
      }
    }
    if (!v.isEmpty()) {
      throw new InvoiceRuleException("The invoice cannot be mapped to an e-invoice", v);
    }
  }

  private static void requireAddress(
      List<Violation> v, String who, Party p, boolean pincodeRequired) {
    if (blank(p.address1())) {
      v.add(new Violation(who + ".address1", "is required for the e-invoice"));
    }
    if (blank(p.location())) {
      v.add(new Violation(who + ".location", "is required for the e-invoice"));
    }
    if (pincodeRequired && blank(p.pincode())) {
      v.add(new Violation(who + ".pincode", "is required for the e-invoice"));
    }
  }

  /**
   * Schema 1.1 text rules: names, optional address line 2, location and descriptions need at least
   * three characters, and no text field may contain a double quote.
   */
  private static void checkPartyText(List<Violation> v, String who, Party p) {
    text(v, who + ".legalName", p.legalName());
    text(v, who + ".tradeName", p.tradeName());
    text(v, who + ".address2", p.address2());
    text(v, who + ".location", p.location());
    if (!blank(p.address1()) && p.address1().indexOf('"') >= 0) {
      v.add(new Violation(who + ".address1", "must not contain '\"' in the e-invoice"));
    }
    if (p.email() != null && !EMAIL.matcher(p.email()).matches()) {
      v.add(
          new Violation(
              who + ".email", "must use only letters, digits and + _ . - for the e-invoice"));
    }
  }

  /** A present text value must be at least three characters long and free of double quotes. */
  private static void text(List<Violation> v, String field, String value) {
    if (blank(value)) {
      return;
    }
    if (value.strip().length() < MIN_TEXT) {
      v.add(new Violation(field, "must be at least 3 characters for the e-invoice"));
    } else if (value.indexOf('"') >= 0) {
      v.add(new Violation(field, "must not contain '\"' in the e-invoice"));
    }
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
