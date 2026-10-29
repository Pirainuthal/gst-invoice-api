package io.github.pirainuthal.gstinvoice.api;

import io.github.pirainuthal.gstinvoice.domain.Hsn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** One invoice line. */
@Schema(description = "Invoice line item")
public record LineItem(
    @Schema(example = "Cotton shirt, half sleeve") @NotBlank @Size(max = 300) String description,
    @Schema(description = "HSN (goods) or SAC (services, starts with 99)", example = "6205")
        @NotBlank
        @Pattern(regexp = Hsn.PATTERN, message = "must be a 4, 6 or 8 digit HSN/SAC code")
        String hsnCode,
    @Schema(example = "10")
        @NotNull
        @DecimalMin(value = "0.001")
        @Digits(integer = 10, fraction = 3)
        BigDecimal quantity,
    @Schema(description = "GST Unit Quantity Code (UQC), e.g. NOS, PCS, KGS", example = "PCS")
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter UQC such as NOS or KGS")
        String unit,
    @Schema(description = "Price per unit, excluding GST", example = "850.00")
        @NotNull
        @PositiveOrZero
        @Digits(integer = 12, fraction = 3)
        BigDecimal unitPrice,
    @Schema(description = "Trade discount in percent of the gross amount", example = "5")
        @DecimalMin("0")
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 3)
        BigDecimal discountPercent,
    @Schema(description = "GST rate in percent (IGST, or CGST+SGST combined)", example = "5")
        @NotNull
        @DecimalMin("0")
        @Digits(integer = 3, fraction = 3)
        BigDecimal gstRate,
    @Schema(description = "Ad-valorem compensation cess in percent (optional)", example = "0")
        @DecimalMin("0")
        @DecimalMax("999.999")
        @Digits(integer = 3, fraction = 3)
        BigDecimal cessRate,
    @Schema(description = "Specific (non ad-valorem) cess in rupees per unit (optional)")
        @DecimalMin("0")
        @Digits(integer = 12, fraction = 3)
        BigDecimal cessPerUnit) {

  /** Discount percent, defaulting to zero. */
  public BigDecimal discountPercentOrZero() {
    return discountPercent == null ? BigDecimal.ZERO : discountPercent;
  }

  /** Ad-valorem cess rate, defaulting to zero. */
  public BigDecimal cessRateOrZero() {
    return cessRate == null ? BigDecimal.ZERO : cessRate;
  }

  /** Specific cess per unit, defaulting to zero. */
  public BigDecimal cessPerUnitOrZero() {
    return cessPerUnit == null ? BigDecimal.ZERO : cessPerUnit;
  }
}
