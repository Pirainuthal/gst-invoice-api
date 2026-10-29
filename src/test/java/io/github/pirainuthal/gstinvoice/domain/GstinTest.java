package io.github.pirainuthal.gstinvoice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GstinTest {

  @Test
  void checksumMatchesSyntheticExample() {
    // 27ABCDE1234F1Z0 is a synthetic example (placeholder PAN ABCDE1234F) for the GSTIN check
    // character.
    assertThat(Gstin.checksum("27ABCDE1234F1Z")).isEqualTo('0');
    assertThat(Gstin.isValid("27ABCDE1234F1Z0")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"33ABCDE1234F1Z7", "29PQRSX5678K1ZU", "04AAAAA1111A1Z4"})
  void acceptsValidGstins(String gstin) {
    assertThat(Gstin.problems(gstin)).isEmpty();
  }

  @Test
  void rejectsWrongCheckCharacter() {
    assertThat(Gstin.problems("27ABCDE1234F1ZW"))
        .singleElement()
        .asString()
        .contains("checksum mismatch")
        .contains("'0'");
  }

  @Test
  void rejectsWrongLength() {
    assertThat(Gstin.problems("27ABCDE1234F1Z")).singleElement().asString().contains("15");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"27ABCDE1234F0Z0", "27ABCD11234F1Z0", "27abcde1234f1z0", "2XABCDE1234F1Z0"})
  void rejectsMalformedLayout(String gstin) {
    assertThat(Gstin.problems(gstin)).singleElement().asString().contains("must match");
  }

  @Test
  void rejectsUnknownOrWithdrawnStateCode() {
    String withdrawn = "25ABCDE1234F1Z";
    assertThat(Gstin.problems(withdrawn + Gstin.checksum(withdrawn)))
        .singleElement()
        .asString()
        .contains("'25'");
    String foreign = "96ABCDE1234F1Z";
    assertThat(Gstin.problems(foreign + Gstin.checksum(foreign))).hasSize(1);
  }

  @Test
  void rejectsBlank() {
    assertThat(Gstin.problems(" ")).containsExactly("GSTIN is empty");
    assertThat(Gstin.problems(null)).containsExactly("GSTIN is empty");
  }

  @Test
  void extractsParts() {
    assertThat(Gstin.stateCode("33ABCDE1234F1Z7")).isEqualTo("33");
    assertThat(Gstin.pan("33ABCDE1234F1Z7")).isEqualTo("ABCDE1234F");
    assertThat(Gstin.normalise(" 33abcde1234f1z7 ")).isEqualTo("33ABCDE1234F1Z7");
  }

  @Test
  void checksumRejectsBadInput() {
    assertThatThrownBy(() -> Gstin.checksum("123")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Gstin.checksum("27ABCDE1234F1#"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
