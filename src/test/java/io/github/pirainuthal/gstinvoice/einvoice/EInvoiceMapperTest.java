package io.github.pirainuthal.gstinvoice.einvoice;

import static io.github.pirainuthal.gstinvoice.Fixtures.buyerKa;
import static io.github.pirainuthal.gstinvoice.Fixtures.item;
import static io.github.pirainuthal.gstinvoice.Fixtures.party;
import static io.github.pirainuthal.gstinvoice.Fixtures.request;
import static io.github.pirainuthal.gstinvoice.Fixtures.seller;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.pirainuthal.gstinvoice.api.DocumentType;
import io.github.pirainuthal.gstinvoice.api.InvoiceRequest;
import io.github.pirainuthal.gstinvoice.api.Party;
import io.github.pirainuthal.gstinvoice.api.SupplyType;
import io.github.pirainuthal.gstinvoice.calc.GstCalculator;
import io.github.pirainuthal.gstinvoice.calc.GstRateCatalog;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException;
import io.github.pirainuthal.gstinvoice.calc.InvoiceRuleException.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;

class EInvoiceMapperTest {

  private final GstCalculator calculator = new GstCalculator(new GstRateCatalog());
  private final EInvoiceMapper mapper = new EInvoiceMapper();
  private final ObjectMapper json = new ObjectMapper();

  private EInvoice map(InvoiceRequest req) {
    return mapper.map(calculator.calculate(req));
  }

  private List<Violation> violations(InvoiceRequest req) {
    try {
      map(req);
    } catch (InvoiceRuleException e) {
      return e.violations();
    }
    throw new AssertionError("expected InvoiceRuleException");
  }

  @Test
  void mapsB2bInterStateInvoice() {
    EInvoice e =
        map(
            request(
                seller(),
                buyerKa(),
                List.of(
                    item("Cotton shirt", "6205", "10", "PCS", "850", "5", "5"),
                    item("Design service", "998391", "1", null, "2000", null, "18"))));

    assertThat(e.version()).isEqualTo("1.1");
    assertThat(e.tranDtls().supTyp()).isEqualTo("B2B");
    assertThat(e.tranDtls().regRev()).isEqualTo("N");
    assertThat(e.docDtls().dt()).isEqualTo("29/10/2026");
    assertThat(e.docDtls().typ()).isEqualTo("INV");
    assertThat(e.sellerDtls().stcd()).isEqualTo("33");
    assertThat(e.sellerDtls().pin()).isEqualTo(641004);
    assertThat(e.buyerDtls().pos()).isEqualTo("29");
    assertThat(e.buyerDtls().gstin()).isEqualTo("29PQRSX5678K1ZU");

    EInvoice.Item goods = e.itemList().get(0);
    assertThat(goods.slNo()).isEqualTo("1");
    assertThat(goods.isServc()).isEqualTo("N");
    assertThat(goods.totAmt()).isEqualByComparingTo("8500.00");
    assertThat(goods.discount()).isEqualByComparingTo("425.00");
    assertThat(goods.assAmt()).isEqualByComparingTo("8075.00");
    assertThat(goods.igstAmt()).isEqualByComparingTo("403.75");
    assertThat(goods.totItemVal()).isEqualByComparingTo("8478.75");
    assertThat(e.itemList().get(1).isServc()).isEqualTo("Y");

    assertThat(e.valDtls().assVal()).isEqualByComparingTo("10075.00");
    assertThat(e.valDtls().igstVal()).isEqualByComparingTo("763.75");
    assertThat(e.valDtls().rndOffAmt()).isEqualByComparingTo("0.25");
    assertThat(e.valDtls().totInvVal()).isEqualByComparingTo("10839.00");
  }

  @Test
  void serialisesWithSchemaFieldNames() throws Exception {
    JsonNode node =
        json.valueToTree(map(request(seller(), buyerKa(), List.of(item("2", "100", "18")))));
    assertThat(node.fieldNames())
        .toIterable()
        .containsExactly(
            "Version", "TranDtls", "DocDtls", "SellerDtls", "BuyerDtls", "ItemList", "ValDtls");
    assertThat(node.at("/TranDtls/EcmGstin").isMissingNode()).isTrue();
    assertThat(node.at("/SellerDtls/Pin").isInt()).isTrue();
    assertThat(node.at("/ItemList/0/GstRt").decimalValue()).isEqualByComparingTo("18");
    assertThat(node.at("/ItemList/0/HsnCd").asText()).isEqualTo("6205");
    assertThat(node.at("/ValDtls/TotInvVal").decimalValue()).isEqualByComparingTo("236.00");
  }

  @Test
  void mapsExportAsUnregisteredRecipientOutsideIndia() {
    EInvoice e =
        map(
            request(
                seller(),
                party("Lakeside Imports Ltd", null, "96"),
                SupplyType.EXPORT_WITHOUT_PAYMENT,
                null,
                List.of(item("100", "450", "5"))));
    assertThat(e.tranDtls().supTyp()).isEqualTo("EXPWOP");
    assertThat(e.buyerDtls().gstin()).isEqualTo("URP");
    assertThat(e.buyerDtls().stcd()).isEqualTo("96");
    assertThat(e.buyerDtls().pos()).isEqualTo("96");
    assertThat(e.buyerDtls().pin()).isEqualTo(999999);
    assertThat(e.valDtls().igstVal()).isEqualByComparingTo("0.00");
  }

  @Test
  void mapsSezAndCreditNoteCodes() {
    InvoiceRequest base =
        request(
            seller(),
            buyerKa(),
            SupplyType.SEZ_WITH_PAYMENT,
            null,
            List.of(item("1", "1000", "18")));
    InvoiceRequest crn =
        new InvoiceRequest(
            "CN-7",
            base.invoiceDate(),
            DocumentType.CRN,
            base.supplyType(),
            base.seller(),
            base.buyer(),
            null,
            base.items(),
            null);
    EInvoice e = map(crn);
    assertThat(e.tranDtls().supTyp()).isEqualTo("SEZWP");
    assertThat(e.docDtls().typ()).isEqualTo("CRN");
    assertThat(e.docDtls().no()).isEqualTo("CN-7");
  }

  @Test
  void rejectsB2cInvoices() {
    assertThat(
            violations(
                request(
                    seller(),
                    party("Walk-in Customer", null, "33"),
                    List.of(item("1", "100", "5")))))
        .extracting(Violation::field)
        .containsExactly("buyer.gstin");
  }

  @Test
  void requiresAddressesUnitsAndDocumentNumberFormat() {
    InvoiceRequest base =
        request(
            seller(),
            new Party("XY", null, "29PQRSX5678K1ZU", "29", null, null, null, null, null, null),
            List.of(item("Widget", "8479", "1", "ABC", "100", null, "18")));
    InvoiceRequest req =
        new InvoiceRequest(
            "0042",
            base.invoiceDate(),
            null,
            null,
            base.seller(),
            base.buyer(),
            null,
            base.items(),
            null);
    assertThat(violations(req))
        .extracting(Violation::field)
        .containsExactly(
            "invoiceNumber",
            "buyer.address1",
            "buyer.location",
            "buyer.pincode",
            "buyer.legalName",
            "items[0].unit");
  }

  @Test
  void enforcesSchemaTextRules() {
    Party buyer =
        new Party(
            "Deccan Retail LLP",
            "DR",
            "29PQRSX5678K1ZU",
            "29",
            "4th Cross, Indiranagar",
            "No",
            "BLR",
            "560038",
            null,
            "o'brien@example.com");
    List<Violation> v =
        violations(
            request(
                seller(),
                buyer,
                List.of(
                    item("TV", "8528", "1", "NOS", "100", null, "18"),
                    item("12\" monitor", "8528", "1", "NOS", "100", null, "18"))));
    assertThat(v)
        .extracting(Violation::field)
        .containsExactly(
            "buyer.tradeName",
            "buyer.address2",
            "buyer.email",
            "items[0].description",
            "items[1].description");
  }
}
