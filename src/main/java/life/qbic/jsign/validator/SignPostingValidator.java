package life.qbic.jsign.validator;

import java.util.List;
import life.qbic.linksmith.model.WebLink;

/**
 * Validates a collection of {@link WebLink}s against a specific Signposting profile
 * or profile level.
 * <p>
 * Implementations of this interface perform semantic checks on already parsed and
 * RFC&nbsp;8288–compliant WebLinks. They do not perform HTTP requests, content
 * negotiation, or dereferencing of link targets.
 * </p>
 *
 * <p>
 * A {@code SignPostingValidator}:
 * </p>
 * <ul>
 *   <li>evaluates the presence, cardinality, and relationships of link relations,</li>
 *   <li>collects all detected violations and recommendations as validation issues, and</li>
 *   <li>returns a {@link SignPostingResult} containing both a semantic view and an issue report.</li>
 * </ul>
 *
 * <p>
 * Validators must be <strong>side effect free</strong> and should not modify the
 * supplied list of WebLinks.
 * </p>
 *
 * <p>
 * Different validators may target different Signposting levels or profiles
 * (e.g. Level 1, Level 2 discovery, or domain-specific extensions) and
 * can be applied independently or in sequence by client code.
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
