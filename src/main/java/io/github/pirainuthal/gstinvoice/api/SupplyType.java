package io.github.pirainuthal.gstinvoice.api;

/**
 * Nature of the supply. Anything other than {@link #REGULAR} is always taxed as inter-state (IGST),
 * per section 7(5) of the IGST Act.
 */
public enum SupplyType {
  /** Domestic supply; intra- or inter-state is decided by seller state vs place of supply. */
  REGULAR(false, false, null),
  /** Supply to an SEZ unit/developer with payment of IGST. */
  SEZ_WITH_PAYMENT(true, false, "SEZWP"),
  /** Supply to an SEZ unit/developer under LUT/bond, without payment of tax. */
  SEZ_WITHOUT_PAYMENT(true, true, "SEZWOP"),
  /** Export with payment of IGST. */
  EXPORT_WITH_PAYMENT(true, false, "EXPWP"),
  /** Export under LUT/bond, without payment of tax. */
  EXPORT_WITHOUT_PAYMENT(true, true, "EXPWOP");

  private final boolean zeroRated;
  private final boolean withoutPayment;
  private final String eInvoiceCode;

  SupplyType(boolean zeroRated, boolean withoutPayment, String eInvoiceCode) {
    this.zeroRated = zeroRated;
    this.withoutPayment = withoutPayment;
    this.eInvoiceCode = eInvoiceCode;
  }

  /** Zero-rated supplies (exports and SEZ supplies) are always inter-state. */
  public boolean zeroRated() {
    return zeroRated;
  }

  /** Supplies under LUT/bond carry no tax amount. */
  public boolean withoutPayment() {
    return withoutPayment;
  }

  /** Whether this is an export (place of supply outside India). */
  public boolean export() {
    return this == EXPORT_WITH_PAYMENT || this == EXPORT_WITHOUT_PAYMENT;
  }

  /** Whether this is a supply to a Special Economic Zone. */
  public boolean sez() {
    return this == SEZ_WITH_PAYMENT || this == SEZ_WITHOUT_PAYMENT;
  }

  /** The e-invoice {@code SupTyp} code; {@code null} for regular (B2B is decided by the buyer). */
  public String eInvoiceCode() {
    return eInvoiceCode;
  }
}
