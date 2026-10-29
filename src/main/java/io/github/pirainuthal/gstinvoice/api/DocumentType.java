package io.github.pirainuthal.gstinvoice.api;

/** Document types defined by the e-invoice schema. */
public enum DocumentType {
  /** Tax invoice. */
  INV("Tax Invoice"),
  /** Credit note. */
  CRN("Credit Note"),
  /** Debit note. */
  DBN("Debit Note");

  private final String title;

  DocumentType(String title) {
    this.title = title;
  }

  /** Title printed at the top of the document. */
  public String title() {
    return title;
  }
}
