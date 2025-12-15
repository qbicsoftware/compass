package life.qbic.compass.spi;

import life.qbic.compass.model.SignPostingView;
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
 * @author Sven Fillinger
 */
public record SignPostingResult(SignPostingView signPostingView, IssueReport issueReport) {
}
