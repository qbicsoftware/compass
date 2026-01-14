package life.qbic.compass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import life.qbic.compass.model.SignPostingView;
import life.qbic.compass.model.SignPostingResult;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.compass.validation.Level1SignPostingValidator;
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

  /**
   * Applies all configured {@link SignPostingValidator}s to the provided WebLinks
   * and aggregates their reported issues into a single {@link SignPostingResult}.
   *
   * <p>
   * Each validator is executed independently and receives the <em>same</em>
   * input list. Validators are not allowed to mutate the input.
   * </p>
   *
   * <h3>Aggregation semantics</h3>
   * <ul>
   *   <li>All validators are executed in the order they were configured.</li>
   *   <li>All {@link life.qbic.linksmith.spi.WebLinkValidator.Issue}s from all
   *       validators are collected and merged into a single {@link IssueReport}.</li>
   *   <li>No short-circuiting occurs: even if one validator reports errors,
   *       subsequent validators are still executed.</li>
   * </ul>
   *
   * <h3>View semantics</h3>
   * <p>
   * The processor is <strong>non-destructive with respect to non-null links</strong>.
   * It does not reorder or modify WebLinks. However, {@code null} elements are
   * <strong>filtered out</strong> before validation and before creating the returned
   * {@link SignPostingView}, because {@code null} values cannot be represented safely
   * in the view API.
   * </p>
   *
   * <p>
   * As a result, the returned {@link SignPostingView} contains all non-null WebLinks
   * from the input list, in their original order.
   * </p>
   * This processor intentionally does <em>not</em> merge or expose any
   * {@link life.qbic.compass.model.Level2LinksetView} instances returned by
   * individual validators. If Level&nbsp;2 structural views are required,
   * clients should invoke the corresponding validator directly
   * (e.g. {@code Level2RecipeValidator}).
   * </p>
   *
   * <h3>Error handling</h3>
   * <ul>
   *   <li>{@code webLinks} must not be {@code null}.</li>
   *   <li>{@code webLinks} may contain {@code null} elements. Null elements are skipped.</li>
   * </ul>
   *
   * @param webLinks the WebLinks to be validated
   * @return a {@link SignPostingResult} containing the aggregated issues and
   *         a {@link SignPostingView} over the input links
   * @throws NullPointerException if {@code webLinks} is {@code null}
   */
  public SignPostingResult process(List<WebLink> webLinks) throws NullPointerException {
    Objects.requireNonNull(webLinks);
    var safeLinks = webLinks.stream()
        .filter(Objects::nonNull)
        .toList();

    var recordedIssues = validators.stream()
        .map(validator -> validator.validate(safeLinks))
        .map(SignPostingResult::issueReport)
        .flatMap(report -> report.issues().stream())
        .toList();

    return new SignPostingResult(new SignPostingView(webLinks), new IssueReport(recordedIssues), null);
  }

  /**
   * Builder for constructing a {@link SignPostingProcessor} with a configurable
   * set of {@link SignPostingValidator}s.
   *
   * <p>
   * Validators are executed in the order they are added to the builder.
   * </p>
   *
   * <p>
   * If no validators are explicitly configured, the processor defaults to
   * using {@link Level1SignPostingValidator}.
   * </p>
   */
  public static final class Builder {

    private List<SignPostingValidator> validators = new ArrayList<>();

    /**
     * Adds one or more validators to this processor.
     *
     * <p>
     * Validators are appended in the order provided.
     * </p>
     *
     * @param validators one or more {@link SignPostingValidator}s
     * @return this builder for fluent chaining
     * @throws NullPointerException if {@code validators} is {@code null}
     */
    Builder withValidators(SignPostingValidator... validators) {
      return this.withValidators(Arrays.stream(validators).toList());
    }

    /**
     * Adds a list of validators to this processor.
     *
     * <p>
     * The provided list is not defensively copied until {@link #build()} is called.
     * </p>
     *
     * @param validators validators to add
     * @return this builder for fluent chaining
     * @throws NullPointerException if {@code validators} is {@code null}
     */
    Builder withValidators(List<SignPostingValidator> validators) {
      this.validators.addAll(validators);
      return this;
    }

    /**
     * Builds a {@link SignPostingProcessor} instance.
     *
     * <p>
     * If no validators were added, the processor is created with a single
     * {@link Level1SignPostingValidator} as a sensible default.
     * </p>
     *
     * @return a configured {@link SignPostingProcessor}
     */
    SignPostingProcessor build() {
      if (validators.isEmpty()) {
        return new SignPostingProcessor(List.of(Level1SignPostingValidator.create()));
      }
      return new SignPostingProcessor(validators);
    }
  }

}
