package life.qbic.compass.model;

import java.util.Objects;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Encapsulates the result of a Signposting profile validation.
 * <p>
 * A {@code SignPostingResult} consists of:
 * </p>
 *
 * <ul>
 *   <li>
 *     a {@link SignPostingView}, which provides a semantic, profile-oriented view
 *     over the supplied {@link WebLink}s, and
 *   </li>
 *   <li>
 *     an {@link IssueReport}, which aggregates all warnings and errors detected
 *     during validation.
 *   </li>
 * <li>
 *     <strong>{@link Level2LinksetView}</strong> (optional),
 *     a structured, domain-oriented interpretation of the validated links
 *     according to <em>FAIR Signposting Level&nbsp;2</em>.
 *     <br>
 *     When present, this view exposes validated resource contexts
 *     (landing pages, content resources, metadata resources) grouped by
 *     their common <em>origin</em>, as defined by the {@code anchor} parameter
 *     in RFC&nbsp;8288 / RFC&nbsp;9264.
 *   </li>
 * </ul>
 *
 * <p>
 * Validation is <strong>non-destructive</strong>: the {@code SignPostingView}
 * always wraps the original WebLinks supplied to the validator, regardless of
 * whether issues were detected.
 * </p>
 *
 * <p>
 * A non-empty {@link IssueReport} does <em>not</em> imply that the SignPostingView
 * is unusable; instead, it indicates deviations from the validated Signposting
 * profile that clients may choose to handle according to their own policies.
 * </p>
 *
 * <p>
 * This design allows clients to:
 * </p>
 * <ul>
 *   <li>inspect validation issues without losing access to parsed link data,</li>
 *   <li>apply multiple Signposting validators independently, and</li>
 *   <li>compose validation results in higher-level workflows.</li>
 * </ul>
 *
 * <h2>Null-handling and optional Level 2 view</h2>
 * <p>
 * {@link #level2LinksetView()} may be {@code null}. This is intentional: not every validation step
 * constructs a Level 2 interpretation. Use {@link #hasLinkSetView()} to check for presence, or wrap it
 * using {@code Optional.ofNullable(result.level2LinksetView())}.
 * </p>
 *
 * @param signPostingView   a read-only view on the validated weblinks
 * @param issueReport       an aggregated report of all recoded issues during validation
 * @param level2LinksetView a Signposting Level 2 compliant view semantics in case the validator
 *                          also performed FAIR Signposting recipe detection (e.g., landing page,
 *                          content or metadata resource)
 * @author Sven Fillinger
 * @since 1.0.0
 */
public record SignPostingResult(
    SignPostingView signPostingView,
    IssueReport issueReport,
    Level2LinksetView level2LinksetView) {

  public SignPostingResult {
    Objects.requireNonNull(signPostingView);
    Objects.requireNonNull(issueReport);
  }

  /**
   * Creates a result without a Level 2 Link Set view.
   *
   * <p>
   * Use this factory when validation did not (or must not) produce a {@link Level2LinksetView}.
   * The returned result will have {@link #level2LinksetView()} set to {@code null}.
   * </p>
   *
   * @param signPostingView a read-only view on the validated weblinks (never {@code null})
   * @param issueReport     an aggregated report of all recorded issues during validation (never {@code null})
   * @return a {@code SignPostingResult} with no Level 2 view
   * @since 1.0.0
   */
  public static SignPostingResult withoutLinksetView(SignPostingView signPostingView, IssueReport issueReport) {
    return new SignPostingResult(signPostingView, issueReport, null);
  }

  /**
   * Creates a result with a non-null Level 2 Link Set view.
   *
   * @param signPostingView   a read-only view on the validated weblinks (never {@code null})
   * @param issueReport       an aggregated report of all recorded issues during validation (never {@code null})
   * @param level2LinksetView a Level 2 interpretation view (must not be {@code null})
   * @return a {@code SignPostingResult} with a Level 2 view attached
   * @throws NullPointerException if {@code level2LinksetView} is {@code null}
   * @since 1.0.0
   */
  public static SignPostingResult withLinksetView(
      SignPostingView signPostingView,
      IssueReport issueReport,
      Level2LinksetView level2LinksetView
  ) {
    Objects.requireNonNull(level2LinksetView, "level2LinksetView");
    return new SignPostingResult(signPostingView, issueReport, level2LinksetView);
  }

  /**
   * Convenience method for aggregators or filters to check, if the current SignPosting result
   * contains a linkset view or not.
   *
   * @return true, if the current Signposting result contains a linkset view, else false
   */
  public boolean hasLinkSetView() {
    return level2LinksetView != null;
  }

  /**
   * Convenience method to check if any issues (warnings or errors) were recorded.
   *
   * @return true if the {@link #issueReport()} contains any issues
   * @since 1.0.0
   */
  public boolean hasIssues() {
    return !issueReport.issues().isEmpty();
  }

  /**
   * Convenience method to check if any errors were recorded.
   *
   * @return true if the {@link #issueReport()} contains errors
   * @since 1.0.0
   */
  public boolean hasErrors() {
    return issueReport.hasErrors();
  }

  /**
   * Convenience method to check if any warnings were recorded.
   *
   * @return true if the {@link #issueReport()} contains warnings
   * @since 1.0.0
   */
  public boolean hasWarnings() {
    return issueReport.hasWarnings();
  }

}
