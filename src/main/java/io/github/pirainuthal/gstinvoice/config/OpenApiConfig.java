package io.github.pirainuthal.gstinvoice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata shown in Swagger UI. */
@Configuration
public class OpenApiConfig {

  /** API description for springdoc. */
  @Bean
  public OpenAPI gstInvoiceOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("GST Invoice API")
                .version("v1")
                .description(
                    "Calculates Indian GST tax invoices (CGST/SGST/UTGST/IGST, cess, round-off,"
                        + " amount in words, HSN summary), renders A4 PDFs and builds e-invoice"
                        + " schema 1.1 JSON payloads. The e-invoice endpoint does not register"
                        + " invoices with the IRP.")
                .contact(new Contact().name("Pirainuthal S").url("https://github.com/Pirainuthal"))
                .license(new License().name("MIT").url("https://opensource.org/license/mit")));
  }
}
