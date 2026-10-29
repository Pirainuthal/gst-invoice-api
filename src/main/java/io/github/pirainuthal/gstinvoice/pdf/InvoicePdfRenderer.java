package io.github.pirainuthal.gstinvoice.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import io.github.pirainuthal.gstinvoice.api.Party;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.HsnSummaryRow;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.LineCalculation;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.TaxRegime;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation.Totals;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException.Violation;
import io.github.pirainuthal.gstinvoice.domain.GstStates;
import io.github.pirainuthal.gstinvoice.domain.IndianNumberFormat;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

/**
 * Renders a calculated invoice as an A4 tax invoice PDF with OpenPDF.
 *
 * <p>Uses the standard Helvetica font (WinAnsi / Windows-1252 encoding), so amounts are prefixed
 * with "INR" rather than the rupee sign, which Helvetica cannot draw. Text that Helvetica cannot
 * encode (Indian scripts, emoji, the rupee sign) is rejected with a rule violation instead of being
 * silently dropped from the PDF.
 */
@Component
public class InvoicePdfRenderer {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd-MMM-uuuu", Locale.ENGLISH);
  private static final Color ACCENT = new Color(0x1F, 0x3A, 0x5F);
  private static final Color HEADER_BG = new Color(0xE8, 0xEE, 0xF5);
  private static final Color RULE = new Color(0xB8, 0xC4, 0xD2);

  private static final Font TITLE = new Font(Font.HELVETICA, 15, Font.BOLD, ACCENT);
  private static final Font SMALL = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.DARK_GRAY);
  private static final Font BODY = new Font(Font.HELVETICA, 8, Font.NORMAL);
  private static final Font BODY_BOLD = new Font(Font.HELVETICA, 8, Font.BOLD);
  private static final Font LABEL = new Font(Font.HELVETICA, 7, Font.BOLD, ACCENT);
  private static final Font TH = new Font(Font.HELVETICA, 7, Font.BOLD, ACCENT);
  private static final Font TOTAL = new Font(Font.HELVETICA, 10, Font.BOLD);

  /** Renders the invoice and returns the PDF bytes. */
  public byte[] render(InvoiceCalculation inv) {
    requirePrintableText(inv);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4, 32, 32, 32, 48);
    try {
      PdfWriter writer = PdfWriter.getInstance(document, out);
      writer.setPageEvent(new Footer(inv.invoiceNumber()));
      document.addTitle(inv.documentType().title() + " " + inv.invoiceNumber());
      document.addAuthor(inv.seller().legalName());
      document.addCreator("gst-invoice-api");
      document.open();

      header(document, inv);
      parties(document, inv);
      items(document, inv);
      totals(document, inv);
      hsnSummary(document, inv);
      endorsement(document, inv);
      signature(document, inv);
    } catch (DocumentException e) {
      throw new IllegalStateException("PDF rendering failed", e);
    } finally {
      if (document.isOpen()) {
        document.close();
      }
    }
    return out.toByteArray();
  }

  private static void header(Document doc, InvoiceCalculation inv) {
    PdfPTable t = new PdfPTable(new float[] {3, 2});
    t.setWidthPercentage(100);
    PdfPCell title =
        cell(new Phrase(inv.documentType().title().toUpperCase(Locale.ROOT), TITLE), false);
    title.setPaddingBottom(6);
    t.addCell(title);
    PdfPCell copy = cell(new Phrase("ORIGINAL FOR RECIPIENT", SMALL), false);
    copy.setHorizontalAlignment(Element.ALIGN_RIGHT);
    copy.setVerticalAlignment(Element.ALIGN_BOTTOM);
    t.addCell(copy);
    doc.add(t);
  }

  private static void parties(Document doc, InvoiceCalculation inv) {
    PdfPTable t = new PdfPTable(new float[] {1, 1});
    t.setWidthPercentage(100);
    t.setSpacingAfter(8);

    t.addCell(boxed(partyBlock("SUPPLIER", inv.seller())));
    Paragraph meta = new Paragraph();
    String docName = inv.documentType().title().replace("Tax ", "");
    meta.add(labelValue(docName + " No", inv.invoiceNumber()));
    meta.add(labelValue(docName + " Date", DATE.format(inv.invoiceDate())));
    meta.add(
        labelValue(
            "Place of Supply", inv.placeOfSupply().code() + " - " + inv.placeOfSupply().name()));
    meta.add(labelValue("Supply Type", supplyLabel(inv)));
    meta.add(labelValue("Reverse Charge", "No"));
    t.addCell(boxed(meta));

    t.addCell(boxed(partyBlock("BILL TO", inv.buyer())));
    Paragraph tax = new Paragraph();
    tax.add(labelValue("Tax Regime", regimeLabel(inv)));
    tax.add(
        labelValue(
            "Amount Payable", "INR " + IndianNumberFormat.amount(inv.totals().grandTotal())));
    t.addCell(boxed(tax));
    doc.add(t);
  }

  private static Paragraph partyBlock(String heading, Party p) {
    Paragraph para = new Paragraph();
    para.add(new Chunk(heading + "\n", LABEL));
    para.add(new Chunk(p.legalName() + "\n", BODY_BOLD));
    if (p.tradeName() != null && !p.tradeName().equals(p.legalName())) {
      para.add(new Chunk("(" + p.tradeName() + ")\n", BODY));
    }
    StringJoiner addr = new StringJoiner(", ");
    for (String part : new String[] {p.address1(), p.address2(), p.location(), p.pincode()}) {
      if (part != null && !part.isBlank()) {
        addr.add(part);
      }
    }
    if (addr.length() > 0) {
      para.add(new Chunk(addr + "\n", BODY));
    }
    String state =
        GstStates.find(p.stateCode()).map(GstStates.State::name).orElse("")
            + " ("
            + p.stateCode()
            + ")";
    para.add(new Chunk("State: " + state + "\n", BODY));
    para.add(new Chunk("GSTIN: " + (p.gstin() == null ? "Unregistered" : p.gstin()), BODY_BOLD));
    return para;
  }

  private static void items(Document doc, InvoiceCalculation inv) {
    boolean intra = inv.taxRegime() == TaxRegime.INTRA_STATE;
    boolean anyCess =
        inv.lines().stream()
            .anyMatch(l -> l.cessAmount().signum() != 0 || l.cessNonAdvolAmount().signum() != 0);
    boolean anyDiscount = inv.lines().stream().anyMatch(l -> l.discountAmount().signum() != 0);

    List<String> heads = new ArrayList<>(List.of("#", "Description", "HSN/SAC", "Qty", "Rate"));
    List<Float> widths = new ArrayList<>(List.of(3f, 24f, 8f, 9f, 9f));
    if (anyDiscount) {
      heads.add("Disc.");
      widths.add(8f);
    }
    heads.add("Taxable");
    widths.add(10f);
    heads.add("GST %");
    widths.add(5f);
    if (intra) {
      heads.add("CGST");
      widths.add(8f);
      heads.add(inv.stateTaxLabel());
      widths.add(8f);
    } else {
      heads.add("IGST");
      widths.add(9f);
    }
    if (anyCess) {
      heads.add("Cess");
      widths.add(7f);
    }
    heads.add("Amount");
    widths.add(10f);

    float[] w = new float[widths.size()];
    for (int i = 0; i < w.length; i++) {
      w[i] = widths.get(i);
    }
    PdfPTable t = new PdfPTable(w);
    t.setWidthPercentage(100);
    t.setHeaderRows(1);
    for (String h : heads) {
      t.addCell(th(h));
    }
    for (LineCalculation l : inv.lines()) {
      t.addCell(td(String.valueOf(l.lineNo()), Element.ALIGN_CENTER));
      t.addCell(td(l.description(), Element.ALIGN_LEFT));
      t.addCell(td(l.hsnCode(), Element.ALIGN_CENTER));
      String unit = l.unit() == null ? "" : " " + l.unit();
      t.addCell(td(l.quantity().stripTrailingZeros().toPlainString() + unit, Element.ALIGN_RIGHT));
      t.addCell(price(l.unitPrice()));
      if (anyDiscount) {
        t.addCell(num(l.discountAmount()));
      }
      t.addCell(num(l.taxableValue()));
      t.addCell(td(l.gstRate().toPlainString(), Element.ALIGN_RIGHT));
      if (intra) {
        t.addCell(num(l.cgstAmount()));
        t.addCell(num(l.sgstAmount()));
      } else {
        t.addCell(num(l.igstAmount()));
      }
      if (anyCess) {
        t.addCell(num(l.cessAmount().add(l.cessNonAdvolAmount())));
      }
      t.addCell(num(l.lineTotal()));
    }
    doc.add(t);
  }

  private static void totals(Document doc, InvoiceCalculation inv) {
    Totals tot = inv.totals();
    PdfPTable outer = new PdfPTable(new float[] {58, 42});
    outer.setWidthPercentage(100);
    outer.setSpacingBefore(6);

    Paragraph words = new Paragraph();
    words.add(new Chunk("AMOUNT IN WORDS\n", LABEL));
    words.add(new Chunk(inv.amountInWords(), BODY_BOLD));
    PdfPCell wordsCell = cell(words, false);
    wordsCell.setPaddingRight(12);
    outer.addCell(wordsCell);

    PdfPTable t = new PdfPTable(new float[] {3, 2});
    t.setWidthPercentage(100);
    if (tot.discountAmount().signum() != 0) {
      totalRow(t, "Gross amount", tot.grossAmount(), BODY);
      totalRow(t, "Less: discount", tot.discountAmount().negate(), BODY);
    }
    totalRow(t, "Taxable value", tot.taxableValue(), BODY);
    if (inv.taxRegime() == TaxRegime.INTRA_STATE) {
      totalRow(t, "CGST", tot.cgstAmount(), BODY);
      totalRow(t, inv.stateTaxLabel(), tot.sgstAmount(), BODY);
    } else {
      totalRow(t, "IGST", tot.igstAmount(), BODY);
    }
    if (tot.cessAmount().signum() != 0) {
      totalRow(t, "Cess", tot.cessAmount(), BODY);
    }
    if (tot.roundOff().signum() != 0) {
      totalRow(t, "Round off", tot.roundOff(), BODY);
    }
    PdfPCell label = cell(new Phrase("Total (INR)", TOTAL), false);
    label.setBorder(Rectangle.TOP);
    label.setBorderColor(ACCENT);
    label.setPaddingTop(4);
    t.addCell(label);
    PdfPCell value = cell(new Phrase(IndianNumberFormat.amount(tot.grandTotal()), TOTAL), false);
    value.setBorder(Rectangle.TOP);
    value.setBorderColor(ACCENT);
    value.setPaddingTop(4);
    value.setHorizontalAlignment(Element.ALIGN_RIGHT);
    t.addCell(value);

    PdfPCell totalsCell = new PdfPCell(t);
    totalsCell.setBorder(Rectangle.NO_BORDER);
    outer.addCell(totalsCell);
    doc.add(outer);
  }

  private static void hsnSummary(Document doc, InvoiceCalculation inv) {
    boolean intra = inv.taxRegime() == TaxRegime.INTRA_STATE;
    Paragraph heading = new Paragraph("HSN/SAC SUMMARY", LABEL);
    heading.setSpacingBefore(10);
    heading.setSpacingAfter(3);
    doc.add(heading);

    PdfPTable t =
        intra
            ? new PdfPTable(new float[] {14, 8, 16, 14, 14, 10, 14})
            : new PdfPTable(new float[] {16, 10, 20, 18, 14, 18});
    t.setWidthPercentage(100);
    t.setHeaderRows(1);
    List<String> heads = new ArrayList<>(List.of("HSN/SAC", "Rate %", "Taxable value"));
    if (intra) {
      heads.addAll(List.of("CGST", inv.stateTaxLabel()));
    } else {
      heads.add("IGST");
    }
    heads.addAll(List.of("Cess", "Total tax"));
    heads.forEach(h -> t.addCell(th(h)));
    for (HsnSummaryRow r : inv.hsnSummary()) {
      t.addCell(td(r.hsnCode(), Element.ALIGN_CENTER));
      t.addCell(td(r.gstRate().toPlainString(), Element.ALIGN_RIGHT));
      t.addCell(num(r.taxableValue()));
      if (intra) {
        t.addCell(num(r.cgstAmount()));
        t.addCell(num(r.sgstAmount()));
      } else {
        t.addCell(num(r.igstAmount()));
      }
      t.addCell(num(r.cessAmount()));
      t.addCell(num(r.totalTax()));
    }
    doc.add(t);
  }

  private static void endorsement(Document doc, InvoiceCalculation inv) {
    String text = null;
    if (inv.supplyType().zeroRated()) {
      String what =
          inv.supplyType().export()
              ? "SUPPLY MEANT FOR EXPORT"
              : "SUPPLY MEANT FOR SUPPLY TO SEZ UNIT OR SEZ DEVELOPER FOR AUTHORISED OPERATIONS";
      text =
          what
              + (inv.supplyType().withoutPayment()
                  ? " UNDER BOND OR LETTER OF UNDERTAKING WITHOUT PAYMENT OF INTEGRATED TAX"
                  : " ON PAYMENT OF INTEGRATED TAX");
    }
    if (text != null) {
      Paragraph p = new Paragraph(text, BODY_BOLD);
      p.setSpacingBefore(8);
      doc.add(p);
    }
  }

  private static void signature(Document doc, InvoiceCalculation inv) {
    PdfPTable t = new PdfPTable(new float[] {1, 1});
    t.setWidthPercentage(100);
    t.setSpacingBefore(18);
    t.setKeepTogether(true);
    PdfPCell note =
        cell(
            new Phrase(
                "Certified that the particulars given above are true and correct.\n"
                    + "This is a computer-generated document.",
                SMALL),
            false);
    note.setVerticalAlignment(Element.ALIGN_BOTTOM);
    t.addCell(note);
    Paragraph sign = new Paragraph();
    sign.add(new Chunk("For " + inv.seller().legalName() + "\n\n\n\n", BODY_BOLD));
    sign.add(new Chunk("Authorised Signatory", BODY));
    PdfPCell signCell = cell(sign, false);
    signCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    t.addCell(signCell);
    doc.add(t);
  }

  /**
   * Rejects text the built-in Helvetica font cannot encode; OpenPDF would otherwise drop those
   * characters without any error, silently changing names and item descriptions.
   */
  private static void requirePrintableText(InvoiceCalculation inv) {
    CharsetEncoder winAnsi = Charset.forName("windows-1252").newEncoder();
    List<Violation> violations = new ArrayList<>();
    printable(violations, winAnsi, "seller", inv.seller());
    printable(violations, winAnsi, "buyer", inv.buyer());
    for (LineCalculation l : inv.lines()) {
      printable(
          violations, winAnsi, "items[" + (l.lineNo() - 1) + "].description", l.description());
    }
    if (!violations.isEmpty()) {
      throw new InvoiceRuleException(
          "The invoice contains text the PDF font cannot print", violations);
    }
  }

  private static void printable(
      List<Violation> violations, CharsetEncoder encoder, String who, Party p) {
    printable(violations, encoder, who + ".legalName", p.legalName());
    printable(violations, encoder, who + ".tradeName", p.tradeName());
    printable(violations, encoder, who + ".address1", p.address1());
    printable(violations, encoder, who + ".address2", p.address2());
    printable(violations, encoder, who + ".location", p.location());
  }

  private static void printable(
      List<Violation> violations, CharsetEncoder encoder, String field, String value) {
    if (value != null && !encoder.canEncode(value)) {
      violations.add(
          new Violation(
              field,
              "contains characters the PDF's Helvetica font cannot print (only Latin-1 / Windows-1252"
                  + " text is supported; write the rupee sign as INR or Rs.)"));
    }
  }

  private static String supplyLabel(InvoiceCalculation inv) {
    return switch (inv.supplyType()) {
      case REGULAR -> inv.buyer().gstin() == null ? "B2C" : "B2B";
      case SEZ_WITH_PAYMENT -> "SEZ with payment";
      case SEZ_WITHOUT_PAYMENT -> "SEZ without payment (LUT)";
      case EXPORT_WITH_PAYMENT -> "Export with payment";
      case EXPORT_WITHOUT_PAYMENT -> "Export without payment (LUT)";
    };
  }

  private static String regimeLabel(InvoiceCalculation inv) {
    return inv.taxRegime() == TaxRegime.INTRA_STATE
        ? "Intra-state (CGST + " + inv.stateTaxLabel() + ")"
        : "Inter-state (IGST)";
  }

  private static Phrase labelValue(String label, String value) {
    Phrase p = new Phrase();
    p.add(new Chunk(label + ": ", LABEL));
    p.add(new Chunk(value + "\n", BODY));
    return p;
  }

  private static void totalRow(PdfPTable t, String label, BigDecimal amount, Font font) {
    t.addCell(cell(new Phrase(label, font), false));
    PdfPCell v = cell(new Phrase(IndianNumberFormat.amount(amount), font), false);
    v.setHorizontalAlignment(Element.ALIGN_RIGHT);
    t.addCell(v);
  }

  private static PdfPCell boxed(Paragraph content) {
    content.setLeading(11.5f);
    PdfPCell c = new PdfPCell();
    c.addElement(content);
    c.setBorderColor(RULE);
    c.setPadding(6);
    c.setPaddingTop(1);
    return c;
  }

  private static PdfPCell cell(Phrase content, boolean border) {
    PdfPCell c = new PdfPCell(content);
    if (!border) {
      c.setBorder(Rectangle.NO_BORDER);
    }
    c.setPadding(2);
    return c;
  }

  private static PdfPCell th(String text) {
    PdfPCell c = new PdfPCell(new Phrase(text, TH));
    c.setBackgroundColor(HEADER_BG);
    c.setBorderColor(RULE);
    c.setHorizontalAlignment(Element.ALIGN_CENTER);
    c.setVerticalAlignment(Element.ALIGN_MIDDLE);
    c.setPadding(4);
    return c;
  }

  private static PdfPCell td(String text, int align) {
    PdfPCell c = new PdfPCell(new Phrase(text, BODY));
    c.setBorderColor(RULE);
    c.setHorizontalAlignment(align);
    c.setPadding(4);
    return c;
  }

  private static PdfPCell price(BigDecimal value) {
    int scale = Math.min(3, Math.max(2, value.stripTrailingZeros().scale()));
    return td(IndianNumberFormat.format(value, scale), Element.ALIGN_RIGHT);
  }

  private static PdfPCell num(BigDecimal value) {
    return td(IndianNumberFormat.amount(value), Element.ALIGN_RIGHT);
  }

  /** Draws "invoice no - page n" at the bottom of each page. */
  private static final class Footer extends PdfPageEventHelper {
    private final String invoiceNumber;

    Footer(String invoiceNumber) {
      this.invoiceNumber = invoiceNumber;
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
      Phrase text = new Phrase(invoiceNumber + "  |  Page " + writer.getPageNumber(), SMALL);
      ColumnText.showTextAligned(
          writer.getDirectContent(),
          Element.ALIGN_CENTER,
          text,
          (document.left() + document.right()) / 2,
          document.bottom() - 20,
          0);
    }
  }
}
