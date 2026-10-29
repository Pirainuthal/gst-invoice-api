package io.github.pirainuthal.gstinvoice.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A supplier or recipient. Address fields are optional for calculation but required for the
 * e-invoice payload.
 */
@Schema(description = "Supplier (seller) or recipient (buyer) of the supply")
public record Party(
    @Schema(example = "Kaveri Loom Works Private Limited") @NotBlank @Size(max = 100)
        String legalName,
    @Schema(example = "Kaveri Looms") @Size(max = 100) String tradeName,
    @Schema(
            description =
                "15-character GSTIN. Required for the seller; omit for unregistered buyers.",
            example = "33ABCDE1234F1Z7")
        @ValidGstin
        String gstin,
    @Schema(description = "Two-digit GST state code of the party's address", example = "33")
        @NotBlank
        @Pattern(regexp = "^\\d{2}$", message = "must be a two-digit GST state code")
        String stateCode,
    @Schema(example = "12, Mill Road") @Size(max = 100) String address1,
    @Schema(example = "Peelamedu") @Size(max = 100) String address2,
    @Schema(example = "Coimbatore") @Size(max = 50) String location,
    @Schema(example = "641004")
        @Pattern(regexp = "^[1-9]\\d{5}$", message = "must be a 6-digit PIN code")
        String pincode,
    @Schema(example = "9876543210")
        @Pattern(regexp = "^\\d{6,12}$", message = "must be 6 to 12 digits")
        String phone,
    @Schema(example = "accounts@example.com") @Email @Size(max = 100) String email) {}
