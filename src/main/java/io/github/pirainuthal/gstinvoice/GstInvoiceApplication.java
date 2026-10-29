package io.github.pirainuthal.gstinvoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point for the GST invoice API. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GstInvoiceApplication {

  /** Starts the application. */
  public static void main(String[] args) {
    SpringApplication.run(GstInvoiceApplication.class, args);
  }
}
