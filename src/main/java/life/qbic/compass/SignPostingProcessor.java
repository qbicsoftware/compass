package life.qbic.compass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import life.qbic.compass.model.SignPostingView;
import life.qbic.compass.spi.SignPostingResult;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.compass.validator.Level1SignPostingValidator;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Orchestrates the evaluation of {@link WebLink}s against one or more Signposting profile
 * validators.
 *
 * <p>
 * The {@code SignPostingProcessor} acts as the main entry point for applying Signposting semantics
 * on top of RFC&nbsp;8288–compliant WebLinks produced by Linksmith. It coordinates one or more
 * {@link SignPostingValidator}s and exposes a consolidated {@link SignPostingResult}.
 * </p>
 *
 * <p>
 * The processor itself does not interpret Signposting rules; all profile-specific logic is
 * delegated to the configured validators. This allows:
 * </p>
 * <ul>
 *   <li>validation of different Signposting levels (e.g. Level&nbsp;1, Level&nbsp;2 discovery),</li>
 *   <li>composition of multiple validators in a single processing step, and</li>
 *   <li>extension with custom or domain-specific Signposting profiles.</li>
 * </ul>
 *
 * <p>
 * Processing is strictly <strong>in-memory and side-effect free</strong>:
 * </p>
 * <ul>
 *   <li>No network requests are performed.</li>
 *   <li>Input WebLinks are not modified.</li>
 *   <li>All validation feedback is reported via the returned {@link SignPostingResult}.</li>
 * </ul>
 *
 * <p>
 * If no validators are explicitly configured, the processor defaults to applying
 * the {@link Level1SignPostingValidator}, ensuring basic FAIR Signposting compliance
 * out of the box.
 * </p>
 *
 * <p>
 * The processor is immutable and thread-safe once built.
 * </p>
 *
 * @author Sven Fillinger
 */
public final class SignPostingProcessor {

  private final List<SignPostingValidator> validators;

  private SignPostingProcessor(List<SignPostingValidator> validators) {
    Objects.requireNonNull(validators);
    this.validators = List.copyOf(validators);
  }

  public SignPostingResult process(List<WebLink> webLinks) throws NullPointerException {
    Objects.requireNonNull(webLinks);

    var recordedIssues = validators.stream()
        .map(validator -> validator.validate(webLinks))
        .map(SignPostingResult::issueReport)
        .flatMap(report -> report.issues().stream())
        .toList();

    return new SignPostingResult(new SignPostingView(webLinks), new IssueReport(recordedIssues));
  }

  public static final class Builder {

    private List<SignPostingValidator> validators = new ArrayList<>();

    Builder withValidators(SignPostingValidator... validators) {
      return this.withValidators(Arrays.stream(validators).toList());
    }

    Builder withValidators(List<SignPostingValidator> validators) {
      this.validators.addAll(validators);
      return this;
    }

    SignPostingProcessor build() {
      if (validators.isEmpty()) {
        return new SignPostingProcessor(List.of(Level1SignPostingValidator.create()));
      }
      return new SignPostingProcessor(validators);
    }
  }

}
