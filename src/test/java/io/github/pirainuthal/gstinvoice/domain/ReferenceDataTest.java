package io.github.pirainuthal.gstinvoice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReferenceDataTest {

  @Test
  void statesDistinguishUtgstJurisdictions() {
    assertThat(GstStates.find("33").orElseThrow().stateTaxLabel()).isEqualTo("SGST");
    assertThat(GstStates.find("07").orElseThrow().stateTaxLabel()).isEqualTo("SGST");
    assertThat(GstStates.find("04").orElseThrow().stateTaxLabel()).isEqualTo("UTGST");
    assertThat(GstStates.find("38").orElseThrow().stateTaxLabel()).isEqualTo("UTGST");
    assertThat(GstStates.find("96").orElseThrow().registrable()).isFalse();
    assertThat(GstStates.find("25")).isEmpty();
    assertThat(GstStates.find("28")).isEmpty();
    assertThat(GstStates.find(null)).isEmpty();
    assertThat(GstStates.all()).hasSize(38);
  }

  @Test
  void hsnRules() {
    assertThat(Hsn.isValid("6205")).isTrue();
    assertThat(Hsn.isValid("998314")).isTrue();
    assertThat(Hsn.isValid("62052000")).isTrue();
    assertThat(Hsn.isValid("62052")).isFalse();
    assertThat(Hsn.isValid("62A5")).isFalse();
    assertThat(Hsn.isValid(null)).isFalse();
    assertThat(Hsn.isService("998314")).isTrue();
    assertThat(Hsn.isService("6205")).isFalse();
  }

  @Test
  void uqcCodes() {
    assertThat(Uqc.isValid("NOS")).isTrue();
    assertThat(Uqc.isValid("nos")).isFalse();
    assertThat(Uqc.isValid("XYZ")).isFalse();
    assertThat(Uqc.isValid(null)).isFalse();
    assertThat(Uqc.describe("KGS")).contains("Kilograms");
  }
}
