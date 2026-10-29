package io.github.pirainuthal.gstinvoice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceApiTest {

  private static final String VALID =
      """
      {
        "invoiceNumber": "KLW/26-27/0042",
        "invoiceDate": "2026-10-29",
        "seller": {
          "legalName": "Kaveri Loom Works Private Limited",
          "gstin": "33ABCDE1234F1Z7",
          "stateCode": "33",
          "address1": "12, Mill Road",
          "location": "Coimbatore",
          "pincode": "641004"
        },
        "buyer": {
          "legalName": "Deccan Retail LLP",
          "gstin": "29PQRSX5678K1ZU",
          "stateCode": "29",
          "address1": "4th Cross, Indiranagar",
          "location": "Bengaluru",
          "pincode": "560038"
        },
        "items": [
          {"description": "Cotton shirt", "hsnCode": "6205", "quantity": 10, "unit": "PCS",
           "unitPrice": 850, "discountPercent": 5, "gstRate": 5},
          {"description": "Pattern design", "hsnCode": "998391", "quantity": 1,
           "unitPrice": 2000, "gstRate": 18}
        ]
      }
      """;

  @Autowired private MockMvc mvc;

  @Test
  void calculatesInvoice() throws Exception {
    mvc.perform(
            post("/api/v1/invoices/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.taxRegime").value("INTER_STATE"))
        .andExpect(jsonPath("$.placeOfSupply.name").value("Karnataka"))
        .andExpect(jsonPath("$.lines", hasSize(2)))
        .andExpect(jsonPath("$.lines[0].taxableValue").value(8075.00))
        .andExpect(jsonPath("$.lines[0].igstAmount").value(403.75))
        .andExpect(jsonPath("$.lines[1].service").value(true))
        .andExpect(jsonPath("$.totals.igstAmount").value(763.75))
        .andExpect(jsonPath("$.totals.roundOff").value(0.25))
        .andExpect(jsonPath("$.totals.grandTotal").value(10839.00))
        .andExpect(
            jsonPath("$.amountInWords").value("Rupees Ten Thousand Eight Hundred Thirty-Nine Only"))
        .andExpect(jsonPath("$.hsnSummary", hasSize(2)));
  }

  @Test
  void bigDecimalsKeepTheirScaleInJson() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/v1/invoices/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID))
            .andReturn();
    String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).contains("\"grandTotal\":10839.00").contains("\"invoiceDate\":\"2026-10-29\"");
  }

  @Test
  void rendersPdf() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/v1/invoices/pdf").contentType(MediaType.APPLICATION_JSON).content(VALID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        startsWith("inline; filename=\"KLW_26-27_0042.pdf\"")))
            .andReturn();
    byte[] pdf = result.getResponse().getContentAsByteArray();
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
  }

  @Test
  void buildsEInvoicePayload() throws Exception {
    mvc.perform(
            post("/api/v1/invoices/e-invoice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.Version").value("1.1"))
        .andExpect(jsonPath("$.TranDtls.SupTyp").value("B2B"))
        .andExpect(jsonPath("$.DocDtls.Dt").value("29/10/2026"))
        .andExpect(jsonPath("$.BuyerDtls.Pos").value("29"))
        .andExpect(jsonPath("$.ItemList[1].IsServc").value("Y"))
        .andExpect(jsonPath("$.ValDtls.TotInvVal").value(10839.00));
  }

  @Test
  void beanValidationErrorsAreProblemJson() throws Exception {
    String bad =
        VALID
            .replace("\"33ABCDE1234F1Z7\"", "\"33ABCDE1234F1Z8\"")
            .replace("\"hsnCode\": \"6205\"", "\"hsnCode\": \"62\"")
            .replace("\"discountPercent\": 5", "\"discountPercent\": 105");
    mvc.perform(
            post("/api/v1/invoices/calculate").contentType(MediaType.APPLICATION_JSON).content(bad))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.instance").value("/api/v1/invoices/calculate"))
        .andExpect(jsonPath("$.errors", hasSize(3)))
        .andExpect(jsonPath("$.errors[0].field").value("items[0].discountPercent"))
        .andExpect(jsonPath("$.errors[1].field").value("items[0].hsnCode"))
        .andExpect(jsonPath("$.errors[2].field").value("seller.gstin"))
        .andExpect(
            jsonPath("$.errors[2].message")
                .value("GSTIN checksum mismatch: last character should be '7' but is '8'"));
  }

  @Test
  void businessRuleErrorsAre422() throws Exception {
    String bad = VALID.replace("\"gstRate\": 18", "\"gstRate\": 7");
    mvc.perform(
            post("/api/v1/invoices/calculate").contentType(MediaType.APPLICATION_JSON).content(bad))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("GST rule violation"))
        .andExpect(jsonPath("$.errors[0].field").value("items[1].gstRate"));
  }

  @Test
  void b2cEInvoiceIs422() throws Exception {
    String b2c = VALID.replace("\"gstin\": \"29PQRSX5678K1ZU\",", "");
    mvc.perform(
            post("/api/v1/invoices/e-invoice").contentType(MediaType.APPLICATION_JSON).content(b2c))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].field").value("buyer.gstin"));
  }

  @Test
  void malformedJsonIs400() throws Exception {
    mvc.perform(
            post("/api/v1/invoices/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("2026-10-29", "29/10/2026")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Malformed request"));
  }

  @Test
  void impossibleDateIs400NotSilentlyAdjusted() throws Exception {
    mvc.perform(
            post("/api/v1/invoices/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID.replace("2026-10-29", "2026-02-30")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Malformed request"));
  }

  @Test
  void missingItemsIs400() throws Exception {
    String noItems = VALID.substring(0, VALID.indexOf("\"items\"")) + "\"items\": []}";
    noItems = noItems.replace("},\n  \"items\"", "}, \"items\"");
    mvc.perform(
            post("/api/v1/invoices/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(noItems))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("items"));
  }

  @Test
  void checksGstin() throws Exception {
    mvc.perform(get("/api/v1/gstin/33abcde1234f1z7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.gstin").value("33ABCDE1234F1Z7"))
        .andExpect(jsonPath("$.stateName").value("Tamil Nadu"))
        .andExpect(jsonPath("$.pan").value("ABCDE1234F"));
    mvc.perform(get("/api/v1/gstin/33ABCDE1234F1Z8"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.problems", hasSize(1)));
  }

  @Test
  void servesOpenApiDocument() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("GST Invoice API"))
        .andExpect(jsonPath("$.paths['/api/v1/invoices/e-invoice'].post").exists());
  }
}
