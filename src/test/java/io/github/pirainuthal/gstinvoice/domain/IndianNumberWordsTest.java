package io.github.pirainuthal.gstinvoice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IndianNumberWordsTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "0|Zero",
        "7|Seven",
        "15|Fifteen",
        "40|Forty",
        "99|Ninety-Nine",
        "100|One Hundred",
        "101|One Hundred One",
        "1000|One Thousand",
        "10001|Ten Thousand One",
        "100000|One Lakh",
        "1234567|Twelve Lakh Thirty-Four Thousand Five Hundred Sixty-Seven",
        "10000000|One Crore",
        "99999999|Nine Crore Ninety-Nine Lakh Ninety-Nine Thousand Nine Hundred Ninety-Nine",
        "1000000000|One Hundred Crore",
        "123456789012|Twelve Thousand Three Hundred Forty-Five Crore Sixty-Seven Lakh Eighty-Nine"
            + " Thousand Twelve"
      })
  void spellsIntegers(long n, String expected) {
    assertThat(IndianNumberWords.words(BigInteger.valueOf(n))).isEqualTo(expected);
  }

  @Test
  void spellsRupeesAndPaise() {
    assertThat(IndianNumberWords.rupees(new BigDecimal("14398.00")))
        .isEqualTo("Rupees Fourteen Thousand Three Hundred Ninety-Eight Only");
    assertThat(IndianNumberWords.rupees(new BigDecimal("1234567.50")))
        .isEqualTo(
            "Rupees Twelve Lakh Thirty-Four Thousand Five Hundred Sixty-Seven and Fifty Paise Only");
    assertThat(IndianNumberWords.rupees(new BigDecimal("0.995"))).isEqualTo("Rupees One Only");
    assertThat(IndianNumberWords.rupees(new BigDecimal("0.05")))
        .isEqualTo("Rupees Zero and Five Paise Only");
  }

  @Test
  void rejectsNegative() {
    assertThatThrownBy(() -> IndianNumberWords.rupees(new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
