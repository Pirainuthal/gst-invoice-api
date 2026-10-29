package io.github.pirainuthal.gstinvoice.pdf;

import static io.github.pirainuthal.gstinvoice.Fixtures.buyerKa;
import static io.github.pirainuthal.gstinvoice.Fixtures.item;
import static io.github.pirainuthal.gstinvoice.Fixtures.party;
import static io.github.pirainuthal.gstinvoice.Fixtures.request;
import static io.github.pirainuthal.gstinvoice.Fixtures.seller;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.pirainuthal.gstinvoice.api.DocumentType;
import io.github.pirainuthal.gstinvoice.api.InvoiceRequest;
import io.github.pirainuthal.gstinvoice.api.LineItem;
import io.github.pirainuthal.gstinvoice.api.SupplyType;
import io.github.pirainuthal.gstinvoice.calc.GstCalculator;
import io.github.pirainuthal.gstinvoice.calc.GstRateCatalog;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException.Violation;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class InvoicePdfRendererTest {

  private final GstCalculator calculator = new GstCalculator(new GstRateCatalog());
  private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

  private static String text(byte[] pdf) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  @Test
  void rendersIntraStateInvoice() throws IOException {
    byte[] pdf =
        renderer.render(
            calculator.calculate(
                request(
                    seller(),
                    party("Nilgiri Stores", "33ABCDE1234F2Z6", "33"),
                    List.of(item("Cotton shirt", "6205", "10", "PCS", "850", "5", "5")))));

    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    String text = text(pdf);
    assertThat(text)
        .contains("TAX INVOICE")
        .contains("KLW/26-27/0042")
        .contains("33ABCDE1234F1Z7")
        .contains("33 - Tamil Nadu")
        .contains("CGST")
        .contains("SGST")
        .contains("8,479.00")
        .contains("Rupees Eight Thousand Four Hundred Seventy-Nine Only")
        .contains("HSN/SAC SUMMARY")
        .contains("Authorised Signatory");
  }

  @Test
  void rendersExportEndorsementAndCess() throws IOException {
    LineItem cessItem =
        new LineItem(
            "Item with cess",
            "24039910",
            BigDecimal.ONE,
            "NOS",
            new BigDecimal("1000"),
            null,
            new BigDecimal("28"),
            new BigDecimal("12"),
            null);
    String inter =
        text(
            renderer.render(calculator.calculate(request(seller(), buyerKa(), List.of(cessItem)))));
    assertThat(inter).contains("IGST").contains("Cess").contains("Inter-state (IGST)");

    String export =
        text(
            renderer.render(
                calculator.calculate(
                    request(
                        seller(),
                        party("Lakeside Imports Ltd", null, "96"),
                        SupplyType.EXPORT_WITHOUT_PAYMENT,
                        null,
                        List.of(item("100", "450", "5"))))));
    assertThat(export)
        .contains("SUPPLY MEANT FOR EXPORT UNDER BOND OR LETTER OF UNDERTAKING")
        .contains("GSTIN: Unregistered")
        .contains("96 - Other Country");
  }

  @Test
  void paginatesLongInvoices() throws IOException {
    List<LineItem> items = new ArrayList<>();
    for (int i = 1; i <= 80; i++) {
      items.add(item("Line " + i, "6205", "1", "PCS", "100", null, "5"));
    }
    byte[] pdf = renderer.render(calculator.calculate(request(seller(), buyerKa(), items)));
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      assertThat(doc.getNumberOfPages()).isGreaterThan(1);
    }
    assertThat(text(pdf)).contains("Page 2");
  }

  @Test
  void rejectsTextHelveticaCannotPrintInsteadOfDroppingIt() {
    var calc =
        calculator.calculate(
            request(
                seller(),
                buyerKa(),
                List.of(
                    item(
                        "\u0B9A\u0B9F\u0BCD\u0B9F\u0BC8 shirt",
                        "6205",
                        "1",
                        "PCS",
                        "100",
                        null,
                        "5"))));
    InvoiceRuleException e =
        catchThrowableOfType(InvoiceRuleException.class, () -> renderer.render(calc));
    assertThat(e.violations()).extracting(Violation::field).containsExactly("items[0].description");
  }

  @Test
  void outputDoesNotDependOnTheDefaultLocale() throws IOException {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      InvoiceRequest base = request(seller(), buyerKa(), List.of(item("1", "100", "5")));
      InvoiceRequest creditNote =
          new InvoiceRequest(
              "CN-7",
              base.invoiceDate(),
              DocumentType.CRN,
              null,
              base.seller(),
              base.buyer(),
              null,
              base.items(),
              null);
      String invoice = text(renderer.render(calculator.calculate(base)));
      assertThat(invoice).contains("TAX INVOICE").contains("Invoice Date: 29-Oct-2026");
      String note = text(renderer.render(calculator.calculate(creditNote)));
      assertThat(note).contains("CREDIT NOTE").contains("Credit Note No: CN-7");
    } finally {
      Locale.setDefault(original);
    }
  }
}
