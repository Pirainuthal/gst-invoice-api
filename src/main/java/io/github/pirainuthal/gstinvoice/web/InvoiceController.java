package io.github.pirainuthal.gstinvoice.web;

import io.github.pirainuthal.gstinvoice.api.InvoiceRequest;
import io.github.pirainuthal.gstinvoice.calc.GstCalculator;
import io.github.pirainuthal.gstinvoice.calc.InvoiceCalculation;
import io.github.pirainuthal.gstinvoice.einvoice.EInvoice;
import io.github.pirainuthal.gstinvoice.einvoice.EInvoiceMapper;
import io.github.pirainuthal.gstinvoice.pdf.InvoicePdfRenderer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Invoice endpoints. */
@RestController
@RequestMapping(path = "/api/v1/invoices", consumes = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Invoices")
@ApiResponse(
    responseCode = "400",
    description = "Malformed JSON or field validation failed",
    content = @Content(mediaType = "application/problem+json"))
@ApiResponse(
    responseCode = "422",
    description = "A GST business rule is violated",
    content = @Content(mediaType = "application/problem+json"))
public class InvoiceController {

  private final GstCalculator calculator;
  private final InvoicePdfRenderer pdfRenderer;
  private final EInvoiceMapper eInvoiceMapper;

  /** Wires the controller. */
  public InvoiceController(
      GstCalculator calculator, InvoicePdfRenderer pdfRenderer, EInvoiceMapper eInvoiceMapper) {
    this.calculator = calculator;
    this.pdfRenderer = pdfRenderer;
    this.eInvoiceMapper = eInvoiceMapper;
  }

  /** Calculates taxes and totals. */
  @Operation(summary = "Calculate a GST invoice")
  @ApiResponse(responseCode = "200", description = "Calculated invoice")
  @PostMapping(path = "/calculate", produces = MediaType.APPLICATION_JSON_VALUE)
  public InvoiceCalculation calculate(@Valid @RequestBody InvoiceRequest request) {
    return calculator.calculate(request);
  }

  /** Renders an A4 PDF tax invoice. */
  @Operation(summary = "Render the invoice as an A4 PDF")
  @ApiResponse(
      responseCode = "200",
      description = "PDF document",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_PDF_VALUE,
              schema = @Schema(type = "string", format = "binary")))
  @PostMapping(path = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> pdf(@Valid @RequestBody InvoiceRequest request) {
    byte[] pdf = pdfRenderer.render(calculator.calculate(request));
    String filename = request.invoiceNumber().replaceAll("[^A-Za-z0-9-]", "_") + ".pdf";
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(filename).build().toString())
        .body(pdf);
  }

  /** Builds the e-invoice (schema 1.1) JSON payload. Does not contact the IRP. */
  @Operation(
      summary = "Build the e-invoice schema 1.1 JSON payload",
      description =
          "Produces the request body for the IRP 'Generate IRN' API. It does NOT register the"
              + " invoice, obtain an IRN or create a signed QR code.")
  @ApiResponse(responseCode = "200", description = "e-invoice JSON payload")
  @PostMapping(path = "/e-invoice", produces = MediaType.APPLICATION_JSON_VALUE)
  public EInvoice eInvoice(@Valid @RequestBody InvoiceRequest request) {
    return eInvoiceMapper.map(calculator.calculate(request));
  }
}
