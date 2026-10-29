package io.github.pirainuthal.gstinvoice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IndianNumberFormatTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "0|0.00",
        "999|999.00",
        "1000|1,000.00",
        "12345.678|12,345.68",
        "100000|1,00,000.00",
        "1234567.5|12,34,567.50",
        "123456789|12,34,56,789.00",
        "-1234.5|-1,234.50"
      })
  void groupsInLakhsAndCrores(String input, String expected) {
    assertThat(IndianNumberFormat.amount(new BigDecimal(input))).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"1499.995|3|1,499.995", "12|0|12"})
  void honoursScale(String input, int scale, String expected) {
    assertThat(IndianNumberFormat.format(new BigDecimal(input), scale)).isEqualTo(expected);
  }
}
