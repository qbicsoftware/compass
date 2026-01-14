package life.qbic.compass.spi;

import java.util.List;
import life.qbic.compass.model.SignPostingResult;
import life.qbic.linksmith.model.WebLink;

/**
 * Validates a collection of {@link WebLink}s against a specific FAIR Signposting profile or profile
 * level.
 *
 * <p>
 * Implementations of this interface perform semantic checks on already parsed and
 * RFC&nbsp;8288–compliant WebLinks. Validators do <strong>not</strong> perform HTTP requests,
 * content negotiation, dereferencing of link targets, or parsing of {@code Link} headers / Link
 * Sets.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Evaluate the presence, cardinality, and relationships of Signposting link relations.</li>
 *   <li>Collect detected violations and recommendations as validation issues.</li>
 *   <li>Return a {@link SignPostingResult} that contains a {@link life.qbic.compass.model.SignPostingView}
 *       and an {@link life.qbic.linksmith.spi.WebLinkValidator.IssueReport}.</li>
 * </ul>
 *
 * <h2>Contract</h2>
 * <h3>Purity / side effects</h3>
 * <ul>
 *   <li>Validators must be <strong>side-effect free</strong>.</li>
 *   <li>Validators must not mutate the supplied {@code webLinks} list or any contained {@link WebLink}.</li>
 * </ul>
 *
 * <h3>Null handling</h3>
 * <ul>
 *   <li>The {@code webLinks} argument itself must not be {@code null}.
 *       Implementations should throw {@link NullPointerException} if it is {@code null}.</li>
 *   <li>The {@code webLinks} list may contain {@code null} elements.
 *       Implementations must <strong>not</strong> throw due to null elements.
 *       Instead, they must:
 *       <ul>
 *         <li>ignore {@code null} entries for validation logic, and</li>
 *         <li>record at least one {@code ERROR} issue describing the presence of {@code null} elements
 *             (best practice: include the index).</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h3>Return value requirements</h3>
 * <ul>
 *   <li>{@link #validate(List)} must return a <strong>non-null</strong> {@link SignPostingResult}.</li>
 *   <li>The returned {@link SignPostingResult#issueReport()} must be non-null.</li>
 *   <li>The returned {@link SignPostingResult#signPostingView()} must be non-null and must not contain
 *       {@code null} WebLinks.</li>
 * </ul>
 *
 * <p>
 * Different validators may target different Signposting levels or recipes
 * (e.g. Level&nbsp;1, Level&nbsp;2 Landing Page / Metadata Resource / Content Resource,
 * or Level&nbsp;2 discovery) and can be applied independently or in sequence by client code.
 * </p>
 *
 * <p>
 * Validators that operate on Level&nbsp;2 Link Sets (i.e. collections of links describing
 * multiple resource origins) may populate the optional
 * {@link SignPostingResult#level2LinksetView()}.
 * If present, it provides a structured, recipe-aware representation of the validated
 * Link Set as a {@link life.qbic.compass.model.Level2LinksetView}.
 * </p>
 */
public interface SignPostingValidator {

  /**
   * Validate the given WebLinks against a Signposting profile.
   *
   * @param webLinks the WebLinks to validate
   * @return the SignPosting validation result
   */
  SignPostingResult validate(List<WebLink> webLinks);

}
