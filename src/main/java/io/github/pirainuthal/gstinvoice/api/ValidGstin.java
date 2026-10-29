package io.github.pirainuthal.gstinvoice.api;

import io.github.pirainuthal.gstinvoice.domain.Gstin;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/** The annotated string, when present, must be a valid GSTIN (format, state code and checksum). */
@Documented
@Constraint(validatedBy = ValidGstin.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGstin {

  /** Fallback message; the validator reports the concrete problem instead. */
  String message() default "invalid GSTIN";

  /** Bean Validation groups. */
  Class<?>[] groups() default {};

  /** Bean Validation payload. */
  Class<? extends Payload>[] payload() default {};

  /** Validator: {@code null} or blank is accepted (use {@code @NotBlank} to require a value). */
  class Validator implements ConstraintValidator<ValidGstin, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null || value.isBlank()) {
        return true;
      }
      List<String> problems = Gstin.problems(value);
      if (problems.isEmpty()) {
        return true;
      }
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(problems.get(0).replace("{", "\\{"))
          .addConstraintViolation();
      return false;
    }
  }
}
