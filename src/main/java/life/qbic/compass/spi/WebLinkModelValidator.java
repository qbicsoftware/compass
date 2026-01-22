package life.qbic.compass.spi;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Contract for validating {@link WebLink} objects at the <em>model level</em>.
 *
 * <p><strong>Audience:</strong> Maintainers of the Compass library.
 * This interface is part of the internal validation layer and is not intended as a public extension
 * point for end users.</p>
 *
 * <h2>Purpose</h2>
 * <p>
 * A {@code WebLinkModelValidator} performs semantic and normative checks on already-parsed
 * {@link WebLink} instances. Unlike parsers, it does not deal with serialization syntax (e.g. HTTP
 * {@code Link} header grammar), but instead validates invariants that must hold for the in-memory
 * model.
 * </p>
 *
 * <p>
 * Typical responsibilities include:
 * </p>
 * <ul>
 *   <li>checking RFC-level constraints that are not guaranteed by parsers,</li>
 *   <li>detecting invalid or inconsistent model states,</li>
 *   <li>reporting violations in a deterministic and non-fatal manner.</li>
 * </ul>
 *
 * <h2>Position in the Compass architecture</h2>
 * <p>
 * Model validators sit <em>below</em> Signposting profile and recipe validators:
 * </p>
 * <ul>
 *   <li>Parsers produce {@link WebLink} instances (possibly permissive).</li>
 *   <li>{@code WebLinkModelValidator}s ensure the model obeys core Web Linking
 *       invariants (e.g. RFC&nbsp;8288 constraints).</li>
 *   <li>Signposting validators operate on a trusted model to apply FAIR-specific semantics.</li>
 * </ul>
 *
 * <p>
 * This separation keeps profile validation logic free from low-level defensive checks
 * and avoids repeating RFC-level validation across multiple validators.
 * </p>
 *
 * <h2>Validation contract</h2>
 * <ul>
 *   <li>The input list itself must not be {@code null}; implementations may throw
 *       {@link NullPointerException} otherwise.</li>
 *   <li>Implementations must be robust against {@code null} elements within the list
 *       and are expected to report them as validation issues rather than failing.</li>
 *   <li>Validation must be <strong>non-destructive</strong>: implementations must not
 *       modify the supplied list or its {@link WebLink} elements.</li>
 *   <li>All findings must be reported via the returned {@link IssueReport};
 *       implementations must not throw on validation failures.</li>
 * </ul>
 *
 * <h2>Error severity</h2>
 * <p>
 * Implementations may distinguish between:
 * </p>
 * <ul>
 *   <li><strong>ERROR</strong> — the model violates a normative requirement
 *       (e.g. invalid URI, missing relation type).</li>
 *   <li><strong>WARNING</strong> — the model is technically valid but suspicious
 *       or problematic for interoperability.</li>
 * </ul>
 *
 * <p>
 * The exact severity mapping is a policy decision of the implementing validator
 * and should be documented and tested accordingly.
 * </p>
 *
 * @author Sven Fillinger
 * @since 1.0.0
 */
public interface WebLinkModelValidator {

  /**
   * Validates a list of {@link WebLink} objects against model-level constraints.
   *
   * <p>
   * Implementations must inspect each element independently and accumulate all detected issues into
   * the returned {@link ModelValidationResult}. Validation must not stop after the first failure.
   * </p>
   *
   * @param webLinks the list of {@link WebLink} instances to validate (must not be {@code null})
   * @return a {@link ModelValidationResult} containing all detected issues and information about
   * which input elements are considered blocking
   * @throws NullPointerException if {@code webLinks} is {@code null}
   */
  ModelValidationResult validate(List<WebLink> webLinks);

  /**
   * Result object returned by {@link WebLinkModelValidator} implementations.
   *
   * <p>
   * The result consists of:
   * </p>
   * <ul>
   *   <li>an {@link IssueReport} containing all detected validation issues, and</li>
   *   <li>a {@link BitSet} indicating which input indices correspond to
   *       <em>blocking</em> model violations.</li>
   * </ul>
   *
   * <p>
   * A blocking index represents a {@link WebLink} that must not be used for
   * downstream semantic processing (e.g. Signposting validation).
   * </p>
   *
   * <p>
   * This record is <strong>immutable</strong>:
   * </p>
   * <ul>
   *   <li>The contained {@link IssueReport} is defensively copied.</li>
   *   <li>The {@link BitSet} is defensively copied using {@link BitSet#clone()}.</li>
   * </ul>
   *
   * <p>
   * Modifying the original {@link IssueReport} or {@link BitSet} passed to the
   * constructor has no effect on the created result instance.
   * </p>
   *
   * @param issueReport     all detected validation issues
   * @param blockingIndices bit set marking indices of blocking WebLinks
   * @since 1.0.0
   */
  record ModelValidationResult(IssueReport issueReport, BitSet blockingIndices) {

    public ModelValidationResult {
      Objects.requireNonNull(issueReport, "issueReport");
      Objects.requireNonNull(blockingIndices, "blockingIndices");

      issueReport = new IssueReport(List.copyOf(issueReport.issues()));
      blockingIndices = (BitSet) blockingIndices.clone();
    }
  }
}
