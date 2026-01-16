package life.qbic.compass.validation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Contract for validating {@link WebLink} objects at the <em>model level</em>.
 *
 * <p><strong>Audience:</strong> Maintainers of the Compass library.
 * This interface is internal to the validation layer and is not intended as a public extension
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
 *   <li>{@code WebLinkModelValidator}s ensure the model obeys core Web Linking rules
 *       (e.g. RFC&nbsp;8288 invariants).</li>
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
interface WebLinkModelValidator {

  /**
   * Validates a list of {@link WebLink} objects against model-level constraints.
   *
   * <p>
   * Implementations must inspect each element independently and accumulate all detected issues into
   * the returned {@link IssueReport}. Validation must not stop after the first failure.
   * </p>
   *
   * @param webLinks the list of {@link WebLink} instances to validate (must not be {@code null})
   * @return an {@link ModelValidationResult} containing all detected issues and the indices of
   * weblinks with recorded ERROR
   * @throws NullPointerException if {@code webLinks} is {@code null}
   */
  ModelValidationResult validate(List<WebLink> webLinks);


  record ModelValidationResult(IssueReport issueReport, boolean[] blockingLinkByIndex) {

    public ModelValidationResult {
      issueReport = new IssueReport(List.copyOf(issueReport.issues()));
      blockingLinkByIndex = Arrays.copyOf(blockingLinkByIndex, blockingLinkByIndex.length);
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ModelValidationResult that = (ModelValidationResult) o;
      return Objects.equals(issueReport, that.issueReport) && Objects.deepEquals(
          blockingLinkByIndex, that.blockingLinkByIndex);
    }

    @Override
    public int hashCode() {
      return Objects.hash(issueReport, Arrays.hashCode(blockingLinkByIndex));
    }

    @Override
    public String toString() {
      return "ModelValidationResult{" +
          "issueReport=" + issueReport +
          ", blockingLinkByIndex=" + Arrays.toString(blockingLinkByIndex) +
          '}';
    }
  }
}
