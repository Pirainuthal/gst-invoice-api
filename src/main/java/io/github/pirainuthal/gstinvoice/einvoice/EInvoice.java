package io.github.pirainuthal.gstinvoice.einvoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.util.List;

/**
 * The e-invoice (FORM GST INV-01) request payload, schema version 1.1, as accepted by the Invoice
 * Registration Portal's "Generate IRN" API. Only the mandatory blocks and the fields this API can
 * fill are modelled; optional blocks (DispDtls, ShipDtls, PayDtls, RefDtls, ExpDtls, EwbDtls) are
 * omitted.
 *
 * <p>Field names are the schema's own PascalCase abbreviations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "Version",
  "TranDtls",
  "DocDtls",
  "SellerDtls",
  "BuyerDtls",
  "ItemList",
  "ValDtls"
})
public record EInvoice(
    @JsonProperty("Version") String version,
    @JsonProperty("TranDtls") TranDtls tranDtls,
    @JsonProperty("DocDtls") DocDtls docDtls,
    @JsonProperty("SellerDtls") SellerDtls sellerDtls,
    @JsonProperty("BuyerDtls") BuyerDtls buyerDtls,
    @JsonProperty("ItemList") List<Item> itemList,
    @JsonProperty("ValDtls") ValDtls valDtls) {

  /** Transaction details. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonPropertyOrder({"TaxSch", "SupTyp", "RegRev", "EcmGstin", "IgstOnIntra"})
  public record TranDtls(
      @JsonProperty("TaxSch") String taxSch,
      @JsonProperty("SupTyp") String supTyp,
      @JsonProperty("RegRev") String regRev,
      @JsonProperty("EcmGstin") String ecmGstin,
      @JsonProperty("IgstOnIntra") String igstOnIntra) {}

  /** Document details; {@code Dt} is {@code dd/MM/yyyy}. */
  @JsonPropertyOrder({"Typ", "No", "Dt"})
  public record DocDtls(
      @JsonProperty("Typ") String typ,
      @JsonProperty("No") String no,
      @JsonProperty("Dt") String dt) {}

  /** Supplier details. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonPropertyOrder({
    "Gstin", "LglNm", "TrdNm", "Addr1", "Addr2", "Loc", "Pin", "Stcd", "Ph", "Em"
  })
  public record SellerDtls(
      @JsonProperty("Gstin") String gstin,
      @JsonProperty("LglNm") String lglNm,
      @JsonProperty("TrdNm") String trdNm,
      @JsonProperty("Addr1") String addr1,
      @JsonProperty("Addr2") String addr2,
      @JsonProperty("Loc") String loc,
      @JsonProperty("Pin") Integer pin,
      @JsonProperty("Stcd") String stcd,
      @JsonProperty("Ph") String ph,
      @JsonProperty("Em") String em) {}

  /** Recipient details; {@code Gstin} is {@code URP} for an unregistered overseas recipient. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonPropertyOrder({
    "Gstin", "LglNm", "TrdNm", "Pos", "Addr1", "Addr2", "Loc", "Pin", "Stcd", "Ph", "Em"
  })
  public record BuyerDtls(
      @JsonProperty("Gstin") String gstin,
      @JsonProperty("LglNm") String lglNm,
      @JsonProperty("TrdNm") String trdNm,
      @JsonProperty("Pos") String pos,
      @JsonProperty("Addr1") String addr1,
      @JsonProperty("Addr2") String addr2,
      @JsonProperty("Loc") String loc,
      @JsonProperty("Pin") Integer pin,
      @JsonProperty("Stcd") String stcd,
      @JsonProperty("Ph") String ph,
      @JsonProperty("Em") String em) {}

  /** One item. Amounts have two decimals; Qty and UnitPrice up to three. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonPropertyOrder({
    "SlNo",
    "PrdDesc",
    "IsServc",
    "HsnCd",
    "Qty",
    "Unit",
    "UnitPrice",
    "TotAmt",
    "Discount",
    "AssAmt",
    "GstRt",
    "IgstAmt",
    "CgstAmt",
    "SgstAmt",
    "CesRt",
    "CesAmt",
    "CesNonAdvlAmt",
    "StateCesRt",
    "StateCesAmt",
    "StateCesNonAdvlAmt",
    "OthChrg",
    "TotItemVal"
  })
  public record Item(
      @JsonProperty("SlNo") String slNo,
      @JsonProperty("PrdDesc") String prdDesc,
      @JsonProperty("IsServc") String isServc,
      @JsonProperty("HsnCd") String hsnCd,
      @JsonProperty("Qty") BigDecimal qty,
      @JsonProperty("Unit") String unit,
      @JsonProperty("UnitPrice") BigDecimal unitPrice,
      @JsonProperty("TotAmt") BigDecimal totAmt,
      @JsonProperty("Discount") BigDecimal discount,
      @JsonProperty("AssAmt") BigDecimal assAmt,
      @JsonProperty("GstRt") BigDecimal gstRt,
      @JsonProperty("IgstAmt") BigDecimal igstAmt,
      @JsonProperty("CgstAmt") BigDecimal cgstAmt,
      @JsonProperty("SgstAmt") BigDecimal sgstAmt,
      @JsonProperty("CesRt") BigDecimal cesRt,
      @JsonProperty("CesAmt") BigDecimal cesAmt,
      @JsonProperty("CesNonAdvlAmt") BigDecimal cesNonAdvlAmt,
      @JsonProperty("StateCesRt") BigDecimal stateCesRt,
      @JsonProperty("StateCesAmt") BigDecimal stateCesAmt,
      @JsonProperty("StateCesNonAdvlAmt") BigDecimal stateCesNonAdvlAmt,
      @JsonProperty("OthChrg") BigDecimal othChrg,
      @JsonProperty("TotItemVal") BigDecimal totItemVal) {}

  /** Invoice value totals. */
  @JsonPropertyOrder({
    "AssVal",
    "CgstVal",
    "SgstVal",
    "IgstVal",
    "CesVal",
    "StCesVal",
    "Discount",
    "OthChrg",
    "RndOffAmt",
    "TotInvVal"
  })
  public record ValDtls(
      @JsonProperty("AssVal") BigDecimal assVal,
      @JsonProperty("CgstVal") BigDecimal cgstVal,
      @JsonProperty("SgstVal") BigDecimal sgstVal,
      @JsonProperty("IgstVal") BigDecimal igstVal,
      @JsonProperty("CesVal") BigDecimal cesVal,
      @JsonProperty("StCesVal") BigDecimal stCesVal,
      @JsonProperty("Discount") BigDecimal discount,
      @JsonProperty("OthChrg") BigDecimal othChrg,
      @JsonProperty("RndOffAmt") BigDecimal rndOffAmt,
      @JsonProperty("TotInvVal") BigDecimal totInvVal) {}
}
