package life.qbic.compass.validation;

import life.qbic.compass.spi.WebLinkModelValidator;

/**
 * Factory and access point for {@link WebLinkModelValidator} implementations
 * provided by Compass.
 *
 * <p>
 * This class centralizes the creation of model-level validators that operate
 * on already parsed {@link life.qbic.linksmith.model.WebLink} instances.
 * It allows the {@link life.qbic.compass.SignPostingProcessor} and client code
 * to obtain well-defined, versioned validation behavior without depending
 * directly on concrete validator classes.
 * </p>
 *
 * <h2>Design intent</h2>
 * <ul>
 *   <li>Decouple processor and builder code from concrete validator implementations</li>
 *   <li>Provide sensible, spec-aligned defaults for model validation</li>
 *   <li>Allow future addition of alternative or stricter model validators
 *       without breaking the public API</li>
 * </ul>
 *
 * <p>
 * Validators returned by this class are expected to:
 * </p>
 * <ul>
 *   <li>be stateless and reusable,</li>
 *   <li>perform in-memory validation only, and</li>
 *   <li>report all findings via {@link life.qbic.linksmith.spi.WebLinkValidator.IssueReport}
 *       rather than throwing exceptions.</li>
 * </ul>
 *
 * <p>
 * This class is intentionally non-instantiable and exposes only static factory methods.
 * </p>
 *
 * @author Sven Fillinger
 * @since 1.0.0
 */
public final class WebLinkModelValidators {

  private WebLinkModelValidators() {}

  /**
   * Returns the default RFC 8288–compliant model validator.
   *
   * <p>
   * The returned validator enforces normative and structural constraints defined
   * by RFC 8288 ("Web Linking") on the {@link life.qbic.linksmith.model.WebLink}
   * model, including:
   * </p>
   * <ul>
   *   <li>absolute target and anchor URIs,</li>
   *   <li>presence and validity of relation types,</li>
   *   <li>parameter name token rules, and</li>
   *   <li>parameter multiplicity constraints.</li>
   * </ul>
   *
   * <p>
   * This validator is used as the <strong>sensible default</strong> by
   * {@link life.qbic.compass.SignPostingProcessor.Builder} unless explicitly
   * overridden by client code.
   * </p>
   *
   * @return an RFC 8288–compliant {@link WebLinkModelValidator}
   */
  public static WebLinkModelValidator rfc8288() {
    return Rfc8288ModelValidator.create();
  }
}
