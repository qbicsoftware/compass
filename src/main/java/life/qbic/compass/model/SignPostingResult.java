package life.qbic.compass.model;

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

  /**
   * Convenience method for aggregators or filters to check, if the current SignPosting result
   * contains a linkset view or not.
   *
   * @return true, if the current Signposting result contains a linkset view, else false
   */
  public boolean hasLinkSetView() {
    return level2LinksetView != null;
  }
}
