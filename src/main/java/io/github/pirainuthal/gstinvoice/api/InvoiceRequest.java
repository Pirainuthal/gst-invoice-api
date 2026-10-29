package io.github.pirainuthal.gstinvoice.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Input for all invoice endpoints. */
@Schema(description = "Invoice to calculate, render or convert to an e-invoice payload")
public record InvoiceRequest(
    @Schema(
            description =
                "Invoice number: up to 16 characters of letters, digits, '/' and '-' (CGST Rule 46)",
            example = "KLW/26-27/0042")
        @NotNull
        @Pattern(
            regexp = "^[A-Za-z0-9/-]{1,16}$",
            message = "must be 1-16 characters of letters, digits, '/' or '-'")
        String invoiceNumber,
    @Schema(
            description = "Invoice date (ISO yyyy-MM-dd); impossible dates are rejected",
            example = "2026-10-29")
        @NotNull
        @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
        LocalDate invoiceDate,
    @Schema(description = "INV (default), CRN or DBN") DocumentType documentType,
    @Schema(description = "REGULAR (default), SEZ_* or EXPORT_*") SupplyType supplyType,
    @NotNull @Valid Party seller,
    @NotNull @Valid Party buyer,
    @Schema(
            description = "Place of supply state code; defaults to the buyer's state code",
            example = "29")
        @Pattern(regexp = "^\\d{2}$", message = "must be a two-digit GST state code")
        String placeOfSupply,
    @NotEmpty @Size(max = 1000) List<@NotNull @Valid LineItem> items,
    @Schema(description = "Round the grand total to the nearest rupee (default true)")
        Boolean roundOff) {

  /** Document type, defaulting to {@link DocumentType#INV}. */
  public DocumentType documentTypeOrDefault() {
    return documentType == null ? DocumentType.INV : documentType;
  }

  /** Supply type, defaulting to {@link SupplyType#REGULAR}. */
  public SupplyType supplyTypeOrDefault() {
    return supplyType == null ? SupplyType.REGULAR : supplyType;
  }

  /** Whether to round the grand total, defaulting to {@code true}. */
  public boolean roundOffOrDefault() {
    return roundOff == null || roundOff;
  }
}
