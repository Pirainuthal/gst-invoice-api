package io.github.pirainuthal.gstinvoice.calc;

import io.github.pirainuthal.gstinvoice.api.DocumentType;
import io.github.pirainuthal.gstinvoice.api.Party;
import io.github.pirainuthal.gstinvoice.api.SupplyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Fully calculated invoice: per-line taxes, HSN summary, totals and words. */
public record InvoiceCalculation(
    String invoiceNumber,
    LocalDate invoiceDate,
    DocumentType documentType,
    SupplyType supplyType,
    Party seller,
    Party buyer,
    PlaceOfSupply placeOfSupply,
    TaxRegime taxRegime,
    String stateTaxLabel,
    List<LineCalculation> lines,
    List<HsnSummaryRow> hsnSummary,
    Totals totals,
    String amountInWords,
    List<String> warnings) {

  /** Whether the supply attracts CGST + SGST/UTGST or IGST. */
  public enum TaxRegime {
    /** Seller state equals place of supply: CGST + SGST (or UTGST). */
    INTRA_STATE,
    /** Different state, export or SEZ supply: IGST. */
    INTER_STATE
  }

  /** Resolved place of supply. */
  public record PlaceOfSupply(String code, String name) {}

  /** Calculated line. Amounts are rounded to paise (half-up). */
  public record LineCalculation(
      int lineNo,
      String description,
      String hsnCode,
      boolean service,
      BigDecimal quantity,
      String unit,
      BigDecimal unitPrice,
      BigDecimal grossAmount,
      BigDecimal discountPercent,
      BigDecimal discountAmount,
      BigDecimal taxableValue,
      BigDecimal gstRate,
      BigDecimal cgstRate,
      BigDecimal cgstAmount,
      BigDecimal sgstRate,
      BigDecimal sgstAmount,
      BigDecimal igstRate,
      BigDecimal igstAmount,
      BigDecimal cessRate,
      BigDecimal cessAmount,
      BigDecimal cessNonAdvolAmount,
      BigDecimal lineTotal) {

    /** All tax on the line, including cess. */
    public BigDecimal totalTax() {
      return cgstAmount.add(sgstAmount).add(igstAmount).add(cessAmount).add(cessNonAdvolAmount);
    }
  }

  /** Tax summary grouped by HSN/SAC and rate (as in GSTR-1 table 12). */
  public record HsnSummaryRow(
      String hsnCode,
      BigDecimal gstRate,
      BigDecimal taxableValue,
      BigDecimal cgstAmount,
      BigDecimal sgstAmount,
      BigDecimal igstAmount,
      BigDecimal cessAmount,
      BigDecimal totalTax) {}

  /** Invoice totals. */
  public record Totals(
      BigDecimal grossAmount,
      BigDecimal discountAmount,
      BigDecimal taxableValue,
      BigDecimal cgstAmount,
      BigDecimal sgstAmount,
      BigDecimal igstAmount,
      BigDecimal cessAmount,
      BigDecimal totalTax,
      BigDecimal totalBeforeRoundOff,
      BigDecimal roundOff,
      BigDecimal grandTotal) {}
}
