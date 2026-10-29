package io.github.pirainuthal.gstinvoice.calc;

import io.github.pirainuthal.gstinvoice.api.InvoiceRequest;
import io.github.pirainuthal.gstinvoice.api.LineItem;
import io.github.pirainuthal.gstinvoice.api.Party;
import io.github.pirainuthal.gstinvoice.api.SupplyType;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.HsnSummaryRow;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.LineCalculation;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.PlaceOfSupply;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.TaxRegime;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.Totals;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException.Violation;
import io.github.pirainuthal.gstinvoice.domain.GstStates;
import io.github.pirainuthal.gstinvoice.domain.Gstin;
import io.github.pirainuthal.gstinvoice.domain.Hsn;
import io.github.pirainuthal.gstinvoice.domain.IndianNumberWords;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * The tax engine. Pure and stateless: the same request always yields the same result.
 *
 * <h2>Rounding policy</h2>
 *
 * <ol>
 *   <li>Gross = quantity &times; unit price, rounded to paise (HALF_UP).
 *   <li>Discount = gross &times; discount% / 100, rounded to paise; taxable value = gross &minus;
 *       discount.
 *   <li>Each tax component (CGST, SGST/UTGST, IGST, cess) is computed per line from the taxable
 *       value and rounded to paise. CGST and SGST are computed independently at half the rate.
 *   <li>Invoice totals are sums of the rounded line amounts.
 *   <li>The grand total is rounded to the nearest rupee (HALF_UP) and the difference is reported as
 *       round-off.
 * </ol>
 */
@Component
public class GstCalculator {

  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final BigDecimal TWO = new BigDecimal("2");
  private static final BigDecimal TWO_HUNDRED = new BigDecimal("200");
  private static final int MONEY_SCALE = 2;
  private static final int RATE_SCALE = 3;

  private final GstRateCatalog rates;

  /** Creates a calculator that validates rates against {@code rates}. */
  public GstCalculator(GstRateCatalog rates) {
    this.rates = rates;
  }

  /**
   * Validates GST business rules and calculates the invoice.
   *
   * @throws InvoiceRuleException if a business rule is violated
   */
  public InvoiceCalculation calculate(InvoiceRequest request) {
    SupplyType supplyType = request.supplyTypeOrDefault();
    String posCode = resolvePlaceOfSupply(request, supplyType);
    validate(request, supplyType, posCode);

    GstStates.State seller = GstStates.find(request.seller().stateCode()).orElseThrow();
    GstStates.State pos = GstStates.find(posCode).orElseThrow();
    boolean interState = supplyType.zeroRated() || !seller.code().equals(pos.code());
    TaxRegime regime = interState ? TaxRegime.INTER_STATE : TaxRegime.INTRA_STATE;

    List<LineCalculation> lines = new ArrayList<>();
    Set<String> warnings = new LinkedHashSet<>();
    int lineNo = 1;
    for (LineItem item : request.items()) {
      lines.add(calculateLine(lineNo++, item, regime, supplyType.withoutPayment()));
      rates.advisory(item.gstRate(), request.invoiceDate()).ifPresent(warnings::add);
    }
    if (supplyType.withoutPayment()) {
      warnings.add(
          "Zero-rated supply under LUT/bond: no tax is charged. Print the LUT/bond reference and"
              + " the endorsement required by rule 46 on the invoice.");
    }

    Totals totals = totals(lines, request.roundOffOrDefault());
    return new InvoiceCalculation(
        request.invoiceNumber(),
        request.invoiceDate(),
        request.documentTypeOrDefault(),
        supplyType,
        normalised(request.seller()),
        normalised(request.buyer()),
        new PlaceOfSupply(pos.code(), pos.name()),
        regime,
        interState ? null : seller.stateTaxLabel(),
        List.copyOf(lines),
        hsnSummary(lines),
        totals,
        IndianNumberWords.rupees(totals.grandTotal()),
        List.copyOf(warnings));
  }

  private static String resolvePlaceOfSupply(InvoiceRequest request, SupplyType supplyType) {
    if (request.placeOfSupply() != null) {
      return request.placeOfSupply();
    }
    return supplyType.export() ? GstStates.OUTSIDE_INDIA : request.buyer().stateCode();
  }

  private void validate(InvoiceRequest request, SupplyType supplyType, String posCode) {
    List<Violation> violations = new ArrayList<>();
    Party seller = request.seller();
    Party buyer = request.buyer();

    var sellerState = GstStates.find(seller.stateCode());
    if (sellerState.filter(GstStates.State::registrable).isEmpty()) {
      violations.add(
          new Violation("seller.stateCode", "'" + seller.stateCode() + "' is not a GST state"));
    }
    if (seller.gstin() == null || seller.gstin().isBlank()) {
      violations.add(
          new Violation("seller.gstin", "a tax invoice must carry the supplier's GSTIN"));
    } else if (!Gstin.stateCode(Gstin.normalise(seller.gstin())).equals(seller.stateCode())) {
      violations.add(
          new Violation(
              "seller.gstin",
              "GSTIN state prefix '"
                  + Gstin.stateCode(Gstin.normalise(seller.gstin()))
                  + "' does not match seller.stateCode '"
                  + seller.stateCode()
                  + "'"));
    }

    if (GstStates.find(buyer.stateCode()).isEmpty()) {
      violations.add(
          new Violation("buyer.stateCode", "'" + buyer.stateCode() + "' is not a GST state"));
    }
    boolean buyerRegistered = buyer.gstin() != null && !buyer.gstin().isBlank();
    if (buyerRegistered
        && !Gstin.stateCode(Gstin.normalise(buyer.gstin())).equals(buyer.stateCode())) {
      violations.add(
          new Violation(
              "buyer.gstin",
              "GSTIN state prefix '"
                  + Gstin.stateCode(Gstin.normalise(buyer.gstin()))
                  + "' does not match buyer.stateCode '"
                  + buyer.stateCode()
                  + "'"));
    }
    if (buyerRegistered
        && seller.gstin() != null
        && Gstin.normalise(buyer.gstin()).equals(Gstin.normalise(seller.gstin()))) {
      violations.add(
          new Violation(
              "buyer.gstin",
              "the recipient's GSTIN must differ from the supplier's; a registration cannot"
                  + " invoice itself"));
    }

    if (GstStates.find(posCode).isEmpty()) {
      violations.add(new Violation("placeOfSupply", "'" + posCode + "' is not a GST state code"));
    }
    boolean posOutsideIndia = GstStates.OUTSIDE_INDIA.equals(posCode);
    if (posOutsideIndia && !supplyType.export()) {
      violations.add(
          new Violation(
              "supplyType",
              "place of supply 96 (outside India) requires supplyType EXPORT_WITH_PAYMENT or"
                  + " EXPORT_WITHOUT_PAYMENT"));
    }
    if (supplyType.export() && !posOutsideIndia) {
      violations.add(
          new Violation("placeOfSupply", "exports must have place of supply 96 (outside India)"));
    }
    if (supplyType.sez() && !buyerRegistered) {
      violations.add(
          new Violation("buyer.gstin", "supplies to an SEZ require the recipient's GSTIN"));
    }

    for (int i = 0; i < request.items().size(); i++) {
      LineItem item = request.items().get(i);
      if (!rates.isAllowed(item.gstRate())) {
        violations.add(
            new Violation(
                "items[" + i + "].gstRate",
                item.gstRate().toPlainString()
                    + "% is not a GST rate; allowed: "
                    + rates.describeAllowed()));
      }
      if (!Hsn.isService(item.hsnCode()) && (item.unit() == null || item.unit().isBlank())) {
        violations.add(new Violation("items[" + i + "].unit", "goods lines require a unit (UQC)"));
      }
    }

    if (!violations.isEmpty()) {
      throw new InvoiceRuleException("The invoice violates GST rules", violations);
    }
  }

  private static LineCalculation calculateLine(
      int lineNo, LineItem item, TaxRegime regime, boolean withoutPayment) {
    BigDecimal gross = money(item.quantity().multiply(item.unitPrice()));
    BigDecimal discountPct = item.discountPercentOrZero();
    BigDecimal discount = money(gross.multiply(discountPct).divide(HUNDRED));
    BigDecimal taxable = gross.subtract(discount);

    BigDecimal rate = item.gstRate();
    BigDecimal zero = money(BigDecimal.ZERO);
    BigDecimal cgstRate = BigDecimal.ZERO;
    BigDecimal igstRate = BigDecimal.ZERO;
    BigDecimal cgst = zero;
    BigDecimal igst = zero;
    if (regime == TaxRegime.INTRA_STATE) {
      cgstRate = rate.divide(TWO);
      cgst = money(taxable.multiply(rate).divide(TWO_HUNDRED));
    } else {
      igstRate = rate;
      igst = withoutPayment ? zero : money(taxable.multiply(rate).divide(HUNDRED));
    }
    BigDecimal sgst = cgst;

    BigDecimal cessRate = item.cessRateOrZero();
    BigDecimal cess = withoutPayment ? zero : money(taxable.multiply(cessRate).divide(HUNDRED));
    BigDecimal cessSpecific =
        withoutPayment ? zero : money(item.quantity().multiply(item.cessPerUnitOrZero()));

    BigDecimal total = taxable.add(cgst).add(sgst).add(igst).add(cess).add(cessSpecific);
    return new LineCalculation(
        lineNo,
        item.description(),
        item.hsnCode(),
        Hsn.isService(item.hsnCode()),
        item.quantity(),
        item.unit(),
        item.unitPrice(),
        gross,
        discountPct,
        discount,
        taxable,
        rate(rate),
        rate(cgstRate),
        cgst,
        rate(cgstRate),
        sgst,
        rate(igstRate),
        igst,
        rate(cessRate),
        cess,
        cessSpecific,
        total);
  }

  private static Totals totals(List<LineCalculation> lines, boolean roundOff) {
    BigDecimal gross = sum(lines, LineCalculation::grossAmount);
    BigDecimal discount = sum(lines, LineCalculation::discountAmount);
    BigDecimal taxable = sum(lines, LineCalculation::taxableValue);
    BigDecimal cgst = sum(lines, LineCalculation::cgstAmount);
    BigDecimal sgst = sum(lines, LineCalculation::sgstAmount);
    BigDecimal igst = sum(lines, LineCalculation::igstAmount);
    BigDecimal cess =
        sum(lines, LineCalculation::cessAmount)
            .add(sum(lines, LineCalculation::cessNonAdvolAmount));
    BigDecimal tax = cgst.add(sgst).add(igst).add(cess);
    BigDecimal beforeRound = taxable.add(tax);
    BigDecimal grand =
        roundOff
            ? beforeRound.setScale(0, RoundingMode.HALF_UP).setScale(MONEY_SCALE)
            : beforeRound;
    return new Totals(
        gross,
        discount,
        taxable,
        cgst,
        sgst,
        igst,
        cess,
        tax,
        beforeRound,
        grand.subtract(beforeRound),
        grand);
  }

  private static List<HsnSummaryRow> hsnSummary(List<LineCalculation> lines) {
    record Key(String hsn, BigDecimal rate) {}
    Comparator<Key> order =
        Comparator.comparing(Key::hsn).thenComparing(Key::rate, BigDecimal::compareTo);
    Map<Key, List<LineCalculation>> groups = new TreeMap<>(order);
    for (LineCalculation line : lines) {
      groups
          .computeIfAbsent(new Key(line.hsnCode(), line.gstRate()), k -> new ArrayList<>())
          .add(line);
    }
    List<HsnSummaryRow> rows = new ArrayList<>();
    groups.forEach(
        (key, group) -> {
          BigDecimal cgst = sum(group, LineCalculation::cgstAmount);
          BigDecimal sgst = sum(group, LineCalculation::sgstAmount);
          BigDecimal igst = sum(group, LineCalculation::igstAmount);
          BigDecimal cess =
              sum(group, LineCalculation::cessAmount)
                  .add(sum(group, LineCalculation::cessNonAdvolAmount));
          rows.add(
              new HsnSummaryRow(
                  key.hsn(),
                  key.rate(),
                  sum(group, LineCalculation::taxableValue),
                  cgst,
                  sgst,
                  igst,
                  cess,
                  cgst.add(sgst).add(igst).add(cess)));
        });
    return List.copyOf(rows);
  }

  private static Party normalised(Party p) {
    String gstin = p.gstin() == null || p.gstin().isBlank() ? null : Gstin.normalise(p.gstin());
    return new Party(
        p.legalName(),
        p.tradeName(),
        gstin,
        p.stateCode(),
        p.address1(),
        p.address2(),
        p.location(),
        p.pincode(),
        p.phone(),
        p.email());
  }

  private static BigDecimal sum(
      List<LineCalculation> lines, Function<LineCalculation, BigDecimal> f) {
    return lines.stream().map(f).reduce(money(BigDecimal.ZERO), BigDecimal::add);
  }

  private static BigDecimal money(BigDecimal value) {
    return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /** Rates are echoed without trailing zeros: 5, 2.5, 0.125. */
  private static BigDecimal rate(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
