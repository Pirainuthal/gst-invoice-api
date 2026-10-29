package io.github.pirainuthal.gstinvoice.calc;

import static io.github.pirainuthal.gstinvoice.Fixtures.CH_GSTIN;
import static io.github.pirainuthal.gstinvoice.Fixtures.KA_GSTIN;
import static io.github.pirainuthal.gstinvoice.Fixtures.TN_GSTIN_2;
import static io.github.pirainuthal.gstinvoice.Fixtures.buyerKa;
import static io.github.pirainuthal.gstinvoice.Fixtures.item;
import static io.github.pirainuthal.gstinvoice.Fixtures.party;
import static io.github.pirainuthal.gstinvoice.Fixtures.request;
import static io.github.pirainuthal.gstinvoice.Fixtures.seller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.pirainuthal.gstinvoice.api.DocumentType;
import io.github.pirainuthal.gstinvoice.api.InvoiceRequest;
import io.github.pirainuthal.gstinvoice.api.LineItem;
import io.github.pirainuthal.gstinvoice.api.Party;
import io.github.pirainuthal.gstinvoice.api.SupplyType;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.HsnSummaryRow;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.LineCalculation;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.TaxRegime;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException.Violation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GstCalculatorTest {

  private final GstCalculator calculator = new GstCalculator(new GstRateCatalog());

  private static BigDecimal bd(String s) {
    return new BigDecimal(s);
  }

  private static Party tnBuyer() {
    return party("Nilgiri Stores", TN_GSTIN_2, "33");
  }

  @Nested
  class IntraState {

    @Test
    void splitsIntoCgstAndSgstWithDiscountAndRoundOff() {
      InvoiceRequest req =
          request(
              seller(),
              tnBuyer(),
              List.of(item("Cotton shirt", "6205", "10", "PCS", "850.00", "5", "5")));

      InvoiceCalculation inv = calculator.calculate(req);
      LineCalculation line = inv.lines().get(0);

      assertThat(inv.taxRegime()).isEqualTo(TaxRegime.INTRA_STATE);
      assertThat(inv.stateTaxLabel()).isEqualTo("SGST");
      assertThat(line.grossAmount()).isEqualByComparingTo("8500.00");
      assertThat(line.discountAmount()).isEqualByComparingTo("425.00");
      assertThat(line.taxableValue()).isEqualByComparingTo("8075.00");
      assertThat(line.cgstRate()).isEqualByComparingTo("2.5");
      // 8075 x 2.5% = 201.875 -> 201.88 (HALF_UP)
      assertThat(line.cgstAmount()).isEqualByComparingTo("201.88");
      assertThat(line.sgstAmount()).isEqualByComparingTo("201.88");
      assertThat(line.igstAmount()).isEqualByComparingTo("0.00");
      assertThat(line.lineTotal()).isEqualByComparingTo("8478.76");
      assertThat(inv.totals().totalBeforeRoundOff()).isEqualByComparingTo("8478.76");
      assertThat(inv.totals().roundOff()).isEqualByComparingTo("0.24");
      assertThat(inv.totals().grandTotal()).isEqualByComparingTo("8479.00");
      assertThat(inv.amountInWords())
          .isEqualTo("Rupees Eight Thousand Four Hundred Seventy-Nine Only");
      assertThat(inv.warnings()).isEmpty();
    }

    @Test
    void usesUtgstLabelInUnionTerritoryWithoutLegislature() {
      Party chSeller = party("Sector 17 Traders", CH_GSTIN, "04");
      Party chBuyer = party("Plaza Retail", null, "04");
      InvoiceCalculation inv =
          calculator.calculate(request(chSeller, chBuyer, List.of(item("1", "1000", "18"))));

      assertThat(inv.taxRegime()).isEqualTo(TaxRegime.INTRA_STATE);
      assertThat(inv.stateTaxLabel()).isEqualTo("UTGST");
      assertThat(inv.totals().cgstAmount()).isEqualByComparingTo("90.00");
      assertThat(inv.totals().sgstAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void handlesFractionalRateSplit() {
      // 0.1% (merchant-export supplies) splits into 0.05% + 0.05%.
      InvoiceCalculation inv =
          calculator.calculate(request(seller(), tnBuyer(), List.of(item("1", "100000", "0.1"))));
      LineCalculation line = inv.lines().get(0);
      assertThat(line.cgstRate()).isEqualByComparingTo("0.05");
      assertThat(line.cgstAmount()).isEqualByComparingTo("50.00");
      assertThat(line.sgstAmount()).isEqualByComparingTo("50.00");
    }
  }

  @Nested
  class InterState {

    @Test
    void chargesIgstWhenPlaceOfSupplyDiffers() {
      InvoiceCalculation inv =
          calculator.calculate(request(seller(), buyerKa(), List.of(item("3", "1499.99", "18"))));
      LineCalculation line = inv.lines().get(0);

      assertThat(inv.taxRegime()).isEqualTo(TaxRegime.INTER_STATE);
      assertThat(inv.stateTaxLabel()).isNull();
      assertThat(inv.placeOfSupply().code()).isEqualTo("29");
      assertThat(inv.placeOfSupply().name()).isEqualTo("Karnataka");
      assertThat(line.grossAmount()).isEqualByComparingTo("4499.97");
      // 4499.97 x 18% = 809.9946 -> 809.99
      assertThat(line.igstAmount()).isEqualByComparingTo("809.99");
      assertThat(line.cgstAmount()).isEqualByComparingTo("0.00");
      assertThat(inv.totals().roundOff()).isEqualByComparingTo("0.04");
      assertThat(inv.totals().grandTotal()).isEqualByComparingTo("5310.00");
    }

    @Test
    void explicitPlaceOfSupplyOverridesBuyerState() {
      // Karnataka-registered buyer, goods delivered in Tamil Nadu: intra-state for a TN seller.
      InvoiceCalculation inv =
          calculator.calculate(
              request(seller(), buyerKa(), null, "33", List.of(item("1", "1000", "18"))));
      assertThat(inv.taxRegime()).isEqualTo(TaxRegime.INTRA_STATE);
      assertThat(inv.totals().cgstAmount()).isEqualByComparingTo("90.00");
    }

    @Test
    void sezSupplyIsInterStateEvenWithinTheSameState() {
      InvoiceCalculation inv =
          calculator.calculate(
              request(
                  seller(),
                  tnBuyer(),
                  SupplyType.SEZ_WITH_PAYMENT,
                  null,
                  List.of(item("1", "1000", "18"))));
      assertThat(inv.taxRegime()).isEqualTo(TaxRegime.INTER_STATE);
      assertThat(inv.totals().igstAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    void cessAdValoremAndSpecific() {
      LineItem tobacco =
          new LineItem(
              "Chewing tobacco",
              "24039910",
              bd("100"),
              "PAC",
              bd("50"),
              null,
              bd("28"),
              bd("12"),
              bd("2.50"));
      InvoiceCalculation inv = calculator.calculate(request(seller(), buyerKa(), List.of(tobacco)));
      LineCalculation line = inv.lines().get(0);
      assertThat(line.igstAmount()).isEqualByComparingTo("1400.00");
      assertThat(line.cessAmount()).isEqualByComparingTo("600.00");
      assertThat(line.cessNonAdvolAmount()).isEqualByComparingTo("250.00");
      assertThat(line.lineTotal()).isEqualByComparingTo("7250.00");
      assertThat(inv.totals().cessAmount()).isEqualByComparingTo("850.00");
      assertThat(inv.totals().totalTax()).isEqualByComparingTo("2250.00");
      assertThat(inv.warnings()).singleElement().asString().contains("28%");
    }
  }

  @Nested
  class ZeroRated {

    @Test
    void exportUnderLutCarriesNoTax() {
      Party foreign = party("Lakeside Imports Ltd", null, "96");
      InvoiceCalculation inv =
          calculator.calculate(
              request(
                  seller(),
                  foreign,
                  SupplyType.EXPORT_WITHOUT_PAYMENT,
                  null,
                  List.of(item("100", "450", "5"))));

      assertThat(inv.placeOfSupply().code()).isEqualTo("96");
      assertThat(inv.taxRegime()).isEqualTo(TaxRegime.INTER_STATE);
      assertThat(inv.lines().get(0).igstRate()).isEqualByComparingTo("5");
      assertThat(inv.totals().igstAmount()).isEqualByComparingTo("0.00");
      assertThat(inv.totals().grandTotal()).isEqualByComparingTo("45000.00");
      assertThat(inv.warnings()).anyMatch(w -> w.contains("LUT"));
    }

    @Test
    void exportWithPaymentChargesIgst() {
      Party foreign = party("Lakeside Imports Ltd", null, "96");
      InvoiceCalculation inv =
          calculator.calculate(
              request(
                  seller(),
                  foreign,
                  SupplyType.EXPORT_WITH_PAYMENT,
                  null,
                  List.of(item("100", "450", "5"))));
      assertThat(inv.totals().igstAmount()).isEqualByComparingTo("2250.00");
    }

    @Test
    void nilRatedLineHasNoTax() {
      InvoiceCalculation inv =
          calculator.calculate(
              request(
                  seller(),
                  tnBuyer(),
                  List.of(item("Fresh vegetables", "0709", "10", "KGS", "40.5", null, "0"))));
      assertThat(inv.totals().totalTax()).isEqualByComparingTo("0.00");
      assertThat(inv.totals().grandTotal()).isEqualByComparingTo("405.00");
      assertThat(inv.totals().roundOff()).isEqualByComparingTo("0.00");
    }
  }

  @Nested
  class Rounding {

    @Test
    void roundsGrandTotalDownBelowHalf() {
      InvoiceCalculation inv =
          calculator.calculate(request(seller(), tnBuyer(), List.of(item("1", "100.49", "0"))));
      assertThat(inv.totals().roundOff()).isEqualByComparingTo("-0.49");
      assertThat(inv.totals().grandTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void roundsHalfUp() {
      InvoiceCalculation inv =
          calculator.calculate(request(seller(), tnBuyer(), List.of(item("1", "100.50", "0"))));
      assertThat(inv.totals().roundOff()).isEqualByComparingTo("0.50");
      assertThat(inv.totals().grandTotal()).isEqualByComparingTo("101.00");
    }

    @Test
    void canDisableRoundOff() {
      InvoiceRequest base = request(seller(), tnBuyer(), List.of(item("1", "100.49", "0")));
      InvoiceRequest req =
          new InvoiceRequest(
              base.invoiceNumber(),
              base.invoiceDate(),
              DocumentType.CRN,
              null,
              base.seller(),
              base.buyer(),
              null,
              base.items(),
              false);
      InvoiceCalculation inv = calculator.calculate(req);
      assertThat(inv.documentType()).isEqualTo(DocumentType.CRN);
      assertThat(inv.totals().roundOff()).isEqualByComparingTo("0.00");
      assertThat(inv.totals().grandTotal()).isEqualByComparingTo("100.49");
      assertThat(inv.amountInWords()).endsWith("and Forty-Nine Paise Only");
    }

    @Test
    void roundsGrossAndDiscountToPaiseBeforeTax() {
      // 3 x 33.333 = 99.999 -> 100.00; 7.5% discount = 7.50; taxable 92.50; 18% split 8.325 -> 8.33
      InvoiceCalculation inv =
          calculator.calculate(
              request(
                  seller(),
                  tnBuyer(),
                  List.of(item("Thread", "5204", "3", "NOS", "33.333", "7.5", "18"))));
      LineCalculation line = inv.lines().get(0);
      assertThat(line.grossAmount()).isEqualByComparingTo("100.00");
      assertThat(line.discountAmount()).isEqualByComparingTo("7.50");
      assertThat(line.taxableValue()).isEqualByComparingTo("92.50");
      assertThat(line.cgstAmount()).isEqualByComparingTo("8.33");
      assertThat(line.sgstAmount()).isEqualByComparingTo("8.33");
      assertThat(line.cgstAmount().scale()).isEqualTo(2);
    }
  }

  @Nested
  class MultipleRates {

    @Test
    void groupsHsnSummaryByCodeAndRate() {
      InvoiceCalculation inv =
          calculator.calculate(
              request(
                  seller(),
                  buyerKa(),
                  List.of(
                      item("Shirt A", "6205", "2", "PCS", "500", null, "5"),
                      item("Design service", "998391", "1", null, "10000", null, "18"),
                      item("Shirt B", "6205", "4", "PCS", "250", null, "5"),
                      item("Premium shirt", "6205", "1", "PCS", "3000", null, "18"))));

      assertThat(inv.hsnSummary())
          .extracting(HsnSummaryRow::hsnCode, r -> r.gstRate().toPlainString())
          .containsExactly(tuple("6205", "5"), tuple("6205", "18"), tuple("998391", "18"));
      HsnSummaryRow shirts5 = inv.hsnSummary().get(0);
      assertThat(shirts5.taxableValue()).isEqualByComparingTo("2000.00");
      assertThat(shirts5.igstAmount()).isEqualByComparingTo("100.00");
      assertThat(inv.lines().get(1).service()).isTrue();

      assertThat(inv.totals().taxableValue()).isEqualByComparingTo("15000.00");
      assertThat(inv.totals().igstAmount()).isEqualByComparingTo("2440.00");
      assertThat(inv.totals().grandTotal()).isEqualByComparingTo("17440.00");
      BigDecimal summaryTax =
          inv.hsnSummary().stream()
              .map(HsnSummaryRow::totalTax)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      assertThat(summaryTax).isEqualByComparingTo(inv.totals().totalTax());
    }
  }

  @Nested
  class Rules {

    private List<Violation> violations(InvoiceRequest req) {
      try {
        calculator.calculate(req);
      } catch (InvoiceRuleException e) {
        return e.violations();
      }
      throw new AssertionError("expected InvoiceRuleException");
    }

    @Test
    void rejectsRateOutsideSlabs() {
      List<Violation> v = violations(request(seller(), buyerKa(), List.of(item("1", "100", "7"))));
      assertThat(v)
          .singleElement()
          .satisfies(
              x -> {
                assertThat(x.field()).isEqualTo("items[0].gstRate");
                assertThat(x.message()).contains("7%").contains("[0, 0.1, 0.25, 1.5, 3, 5");
              });
    }

    @Test
    void acceptsRateWithTrailingZeros() {
      assertThat(
              calculator
                  .calculate(request(seller(), buyerKa(), List.of(item("1", "100", "18.00"))))
                  .lines()
                  .get(0)
                  .gstRate()
                  .toPlainString())
          .isEqualTo("18");
    }

    @Test
    void rejectsGstinStateMismatch() {
      Party wrong = party("Kaveri Loom Works", KA_GSTIN, "33");
      Party buyer = party("Some Buyer", "29PQRSX5678K2ZT", "33");
      List<Violation> v = violations(request(wrong, buyer, List.of(item("1", "100", "5"))));
      assertThat(v).extracting(Violation::field).containsExactly("seller.gstin", "buyer.gstin");
    }

    @Test
    void requiresSellerGstinAndKnownStates() {
      Party noGstin = party("Kaveri Loom Works", null, "25");
      Party buyer = party("Some Buyer", null, "00");
      List<Violation> v = violations(request(noGstin, buyer, List.of(item("1", "100", "5"))));
      assertThat(v)
          .extracting(Violation::field)
          .containsExactly("seller.stateCode", "seller.gstin", "buyer.stateCode", "placeOfSupply");
    }

    @Test
    void outsideIndiaRequiresExportSupplyType() {
      List<Violation> v =
          violations(
              request(
                  seller(), party("Lakeside Imports", null, "96"), List.of(item("1", "100", "5"))));
      assertThat(v).extracting(Violation::field).containsExactly("supplyType");
    }

    @Test
    void exportRequiresPlaceOfSupplyOutsideIndia() {
      List<Violation> v =
          violations(
              request(
                  seller(),
                  buyerKa(),
                  SupplyType.EXPORT_WITH_PAYMENT,
                  "29",
                  List.of(item("1", "100", "5"))));
      assertThat(v).extracting(Violation::field).containsExactly("placeOfSupply");
    }

    @Test
    void sezRequiresRecipientGstin() {
      List<Violation> v =
          violations(
              request(
                  seller(),
                  party("SEZ Unit", null, "33"),
                  SupplyType.SEZ_WITHOUT_PAYMENT,
                  null,
                  List.of(item("1", "100", "5"))));
      assertThat(v).extracting(Violation::field).containsExactly("buyer.gstin");
    }

    @Test
    void rejectsInvoiceToOwnGstin() {
      Party self = party("Kaveri Loom Works", "33abcde1234f1z7", "33");
      List<Violation> v = violations(request(seller(), self, List.of(item("1", "100", "5"))));
      assertThat(v).extracting(Violation::field).containsExactly("buyer.gstin");
    }

    @Test
    void goodsNeedUnitButServicesDoNot() {
      List<Violation> v =
          violations(
              request(
                  seller(),
                  buyerKa(),
                  List.of(
                      item("Shirt", "6205", "1", null, "100", null, "5"),
                      item("Consulting", "998311", "1", null, "100", null, "18"))));
      assertThat(v).extracting(Violation::field).containsExactly("items[0].unit");
    }
  }

  @Nested
  class Advisories {

    @Test
    void warnsAboutTwelvePercentAfterRationalisation() {
      InvoiceCalculation inv =
          calculator.calculate(request(seller(), buyerKa(), List.of(item("1", "100", "12"))));
      assertThat(inv.warnings()).singleElement().asString().contains("22-Sep-2025");
    }

    @Test
    void warnsAboutFortyPercentBeforeRationalisation() {
      GstRateCatalog catalog = new GstRateCatalog();
      assertThat(catalog.advisory(bd("40"), LocalDate.of(2025, 9, 21))).isPresent();
      assertThat(catalog.advisory(bd("40"), LocalDate.of(2025, 9, 22))).isEmpty();
      assertThat(catalog.advisory(bd("12"), LocalDate.of(2025, 9, 21))).isEmpty();
      assertThat(catalog.advisory(bd("18"), LocalDate.of(2026, 1, 1))).isEmpty();
    }

    @Test
    void allowedRatesAreConfigurable() {
      GstRateCatalog catalog = new GstRateCatalog();
      catalog.setAllowed(List.of(bd("5"), bd("18")));
      assertThat(catalog.isAllowed(bd("12"))).isFalse();
      assertThat(catalog.describeAllowed()).isEqualTo("[5, 18]");
    }
  }
}
