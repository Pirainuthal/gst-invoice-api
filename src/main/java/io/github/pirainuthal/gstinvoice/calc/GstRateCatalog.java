package io.github.pirainuthal.gstinvoice.calc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The GST rates the API accepts, and advisory notes for rates whose use changed with the
 * rationalisation effective 22 September 2025 (Notification 9/2025-Central Tax (Rate)).
 *
 * <p>Configure with {@code gst.rates.allowed} if a notified rate is missing from the defaults.
 */
@ConfigurationProperties(prefix = "gst.rates")
public class GstRateCatalog {

  /** Date from which the 5% / 18% / 40% structure applies. */
  public static final LocalDate RATIONALISATION_DATE = LocalDate.of(2025, 9, 22);

  private static final BigDecimal TWELVE = new BigDecimal("12");
  private static final BigDecimal TWENTY_EIGHT = new BigDecimal("28");
  private static final BigDecimal FORTY = new BigDecimal("40");

  private List<BigDecimal> allowed =
      List.of(
          new BigDecimal("0"),
          new BigDecimal("0.1"),
          new BigDecimal("0.25"),
          new BigDecimal("1.5"),
          new BigDecimal("3"),
          new BigDecimal("5"),
          TWELVE,
          new BigDecimal("18"),
          TWENTY_EIGHT,
          FORTY);

  /** Accepted combined GST rates in percent. */
  public List<BigDecimal> getAllowed() {
    return allowed;
  }

  /** Replaces the accepted rates (bound from configuration). */
  public void setAllowed(List<BigDecimal> allowed) {
    this.allowed = List.copyOf(allowed);
  }

  /** Whether {@code rate} is one of the accepted rates, ignoring trailing zeros. */
  public boolean isAllowed(BigDecimal rate) {
    return allowed.stream().anyMatch(r -> r.compareTo(rate) == 0);
  }

  /** Accepted rates, sorted and without trailing zeros, for error messages. */
  public String describeAllowed() {
    TreeSet<BigDecimal> sorted = new TreeSet<>();
    allowed.forEach(r -> sorted.add(r.stripTrailingZeros()));
    return sorted.stream().map(BigDecimal::toPlainString).toList().toString();
  }

  /** An advisory note when a rate is unusual for the invoice date; never blocks calculation. */
  public Optional<String> advisory(BigDecimal rate, LocalDate invoiceDate) {
    boolean afterRationalisation = !invoiceDate.isBefore(RATIONALISATION_DATE);
    if (afterRationalisation && rate.compareTo(TWELVE) == 0) {
      return Optional.of(
          "12% GST was removed for most goods and services from 22-Sep-2025 (items moved to 5% or"
              + " 18%); confirm the HSN/SAC still attracts 12%.");
    }
    if (afterRationalisation && rate.compareTo(TWENTY_EIGHT) == 0) {
      return Optional.of(
          "From 22-Sep-2025 the 28% rate is retained only for specified tobacco and pan masala"
              + " items; confirm the HSN still attracts 28%.");
    }
    if (!afterRationalisation && rate.compareTo(FORTY) == 0) {
      return Optional.of("The 40% GST rate applies only to supplies on or after 22-Sep-2025.");
    }
    return Optional.empty();
  }
}
