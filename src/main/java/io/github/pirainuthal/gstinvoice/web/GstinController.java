package io.github.pirainuthal.gstinvoice.web;

import io.github.pirainuthal.gstinvoice.domain.GstStates;
import io.github.pirainuthal.gstinvoice.domain.Gstin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Offline GSTIN checks (format, state code, checksum). No portal lookup is performed. */
@RestController
@RequestMapping(path = "/api/v1/gstin", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "GSTIN")
public class GstinController {

  /** Result of an offline GSTIN check. */
  public record GstinCheck(
      String gstin,
      boolean valid,
      String stateCode,
      String stateName,
      String pan,
      List<String> problems) {}

  /** Validates a GSTIN offline. */
  @Operation(
      summary = "Validate a GSTIN offline",
      description =
          "Checks structure, state code and the mod-36 check character. It does not confirm that"
              + " the GSTIN is registered or active on the GST portal.")
  @GetMapping("/{gstin}")
  public GstinCheck check(@PathVariable String gstin) {
    String normalised = Gstin.normalise(gstin);
    List<String> problems = Gstin.problems(normalised);
    if (!problems.isEmpty()) {
      return new GstinCheck(normalised, false, null, null, null, problems);
    }
    String state = Gstin.stateCode(normalised);
    return new GstinCheck(
        normalised,
        true,
        state,
        GstStates.find(state).map(GstStates.State::name).orElse(null),
        Gstin.pan(normalised),
        problems);
  }
}
