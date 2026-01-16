package life.qbic.compass.validation;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.model.WebLinkParameter;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Internal, model-level validator for RFC 8288 ("Web Linking") constraints.
 *
 * <p><strong>Audience:</strong> This class is package-private and intended for maintainers of the
 * Compass library. It is <em>not</em> a public API and its exact issue wording is allowed to
 * change. Tests should primarily assert on the presence and type of issues (ERROR/WARNING) and on
 * stable message fragments.</p>
 *
 * <h2>Scope</h2>
 * <p>
 * This validator checks the <em>in-memory</em> {@link WebLink} model for normative and structural
 * requirements that follow from RFC 8288 and closely related ABNF/token rules (e.g. RFC 7230 token
 * production used by RFC 8288 for parameter names). It does not parse header field syntax; it
 * assumes parsing already happened upstream (e.g. by Linksmith or other tooling).
 * </p>
 *
 * <h2>Why this exists in Compass</h2>
 * <p>
 * Linksmith's {@link WebLink} model is intentionally permissive. Compass operates on
 * {@link WebLink} objects that may originate from different sources and therefore cannot assume
 * that upstream parsing/validation has enforced all RFC 8288 requirements. This validator provides
 * a deterministic safety net at the model boundary.
 * </p>
 *
 * <h2>Policy decisions encoded here</h2>
 * <ul>
 *   <li><strong>Null safety:</strong> {@code null} list entries are reported as {@code ERROR} and skipped.</li>
 *   <li><strong>Relative URIs in the model are errors:</strong>
 *       RFC 8288 parsers are expected to resolve URI-references against a base URI. Therefore,
 *       a relative {@code target} or {@code anchor} in the model is treated as a normative violation
 *       of the producer of the model and reported as {@code ERROR}.</li>
 *   <li><strong>Target scheme warnings:</strong> Non-http(s) absolute targets are still legal URIs,
 *       but Compass warns because most signposting usage expects web-resolvable HTTP(S) identifiers.</li>
 *   <li><strong>Relation type tokens:</strong> A relation type token is accepted if it is either a valid
 *       absolute URI (extension relation type) or matches the registered relation token ABNF.</li>
 *   <li><strong>Parameter name tokens:</strong> Parameter names must match RFC 7230 token rules.</li>
 *   <li><strong>Parameter multiplicity:</strong> Target attributes (parameters) are checked for duplicates,
 *       allowing repeated {@code hreflang} only. All other repeated parameters are reported.</li>
 * </ul>
 *
 * <h2>Known limitations / deliberate non-goals</h2>
 * <ul>
 *   <li>This class does not attempt to validate full RFC 8288 header field syntax or quoting rules.</li>
 *   <li>This class does not validate that {@code rel} occurred exactly once as a serialized parameter,
 *       because the {@link WebLink} API exposes relation values via {@link WebLink#rel()} (a derived view).
 *       If you need enforcement of "rel MUST NOT appear more than once", it must be done at the
 *       parameter layer (i.e. by inspecting {@link WebLink#params()} for repeated {@code rel} parameters).</li>
 *   <li>This class does not enforce constraints of specific relation types (e.g. signposting recipes);
 *       that is done by Compass recipe validators.</li>
 * </ul>
 *
 * @author Sven Fillinger
 * @since 1.0.0
 */
class Rfc8288ModelValidator implements WebLinkModelValidator {

  /**
   * Pattern for a <em>registered</em> relation type token ("reg-rel-type") as defined by RFC 8288.
   *
   * <pre>{@code
   * relation-type  = reg-rel-type / ext-rel-type
   * reg-rel-type   = LOALPHA *( LOALPHA / DIGIT / "." / "-" )
   * ext-rel-type   = URI
   * }</pre>
   *
   * <p>
   * This pattern matches the registered token form and is compiled case-insensitive because RFC
   * 8288 specifies case-insensitive comparison for registered relation types.
   * </p>
   *
   * <p><strong>Note for maintainers:</strong> Case-insensitive comparison does not mean the token
   * grammar accepts arbitrary case; it means comparisons are performed case-insensitively.
   * Accepting mixed case here is therefore an interoperability-friendly choice.
   * </p>
   */
  private static final Pattern REGULAR_RELATION_TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9.-]*$",
      Pattern.CASE_INSENSITIVE);

  /**
   * RFC 7230 "token" character class used by RFC 8288 for parameter names.
   *
   * <p>
   * RFC 8288 uses HTTP header parameter conventions, which rely on RFC 7230 token syntax. This
   * pattern is applied to {@link WebLinkParameter#name()}.
   * </p>
   */
  private static final Pattern ALLOWED_TOKEN_CHARS = Pattern.compile(
      "^[!#$%&'*+-.^_`|~0-9A-Za-z]+$");

  /**
   * Factory for internal use.
   *
   * <p>
   * Kept package-private intentionally. The class is internal; callers should use Compass entry
   * points.
   * </p>
   *
   * @return a new {@link Rfc8288ModelValidator}
   */
  static Rfc8288ModelValidator create() {
    return new Rfc8288ModelValidator();
  }

  /**
   * Validates a list of {@link WebLink} objects for RFC 8288 model constraints.
   *
   * <p>
   * The input list must not be {@code null}. Individual {@code null} elements are reported as
   * {@code ERROR} and skipped to keep validation robust and deterministic.
   * </p>
   *
   * <p>
   * The returned {@link IssueReport} contains all findings; validation is intentionally non-fatal
   * and does not stop after the first violation.
   * </p>
   *
   * @param webLinks list of model links to validate (must not be {@code null})
   * @return an {@link ModelValidationResult} containing all detected issues and the indices of
   * weblinks with recorded ERROR
   * @throws NullPointerException if {@code webLinks} is {@code null}
   */
  @Override
  public ModelValidationResult validate(List<WebLink> webLinks) {
    // Throws NPE early
    Objects.requireNonNull(webLinks);
    var issueSink = new IssueSink(new ArrayList<>(), new boolean[webLinks.size()], 0);
    for (int index = 0; index < webLinks.size(); index++) {
      issueSink.currentIndex = index;
      var currentLink = webLinks.get(index);
      if (currentLink == null) {
        issueSink.addError("Element is null at index %d".formatted(index));
        continue;
      }
      validate(currentLink, issueSink);
    }
    return new ModelValidationResult(new IssueReport(issueSink.issues), issueSink.blockingLinkIndices);
  }

  /**
   * Runs all model-level checks for a single {@link WebLink}.
   *
   * <p>
   * The checks are intentionally ordered so that "cheap and structural" validations happen early
   * (target URI, relation presence) before more detailed validations (relation token grammar,
   * parameter name token rules, multiplicity). This ordering improves debugging signal.
   * </p>
   *
   * @param currentLink the link to validate (non-null)
   * @param issueSink   a container with the recorded issues, the current index and the recorded
   *                    blocking weblinks
   */
  private static void validate(WebLink currentLink, IssueSink issueSink) {
    validateTargetUri(currentLink.target(), issueSink);
    validateRelationPresence(currentLink, issueSink);
    validateRelationTypeToken(currentLink.rel(), issueSink);
    validateParameterNames(currentLink, issueSink);
    validateTargetAttributeCardinality(currentLink.params(), issueSink);
    currentLink.anchor().ifPresent(anchor -> validateAnchorAttribute(anchor, issueSink));

  }

  /**
   * Validates the {@code anchor} parameter value, if present.
   *
   * <p>
   * Policy: the model must contain an absolute URI for {@code anchor}. While RFC 8288 allows
   * URI-references, a parser is expected to resolve them to absolute URIs at model construction
   * time. Therefore, a relative anchor indicates a producer/parser contract violation and is an
   * {@code ERROR}.
   * </p>
   *
   * @param anchor    raw anchor value from {@link WebLink#anchor()}
   * @param issueSink a container with the recorded issues, the current index and the recorded
   *                  blocking weblinks
   */
  private static void validateAnchorAttribute(String anchor, IssueSink issueSink) {
    if (anchor.isBlank()) {
      issueSink.addError("Anchor attribute value is empty for element at index %d".formatted(
          issueSink.currentIndex));
      return;
    }
    try {
      var uri = URI.create(anchor);
      if (!uri.isAbsolute()) {
        issueSink.addError(
            "Invalid anchor value. URI is not absolute for element at index %d".formatted(
                issueSink.currentIndex));
      }
    } catch (IllegalArgumentException ignored) {
      issueSink.addError(
          "Invalid anchor attribute value. '%s' is not an URI for element at index %d".formatted(
              anchor, issueSink.currentIndex));
    }
  }

  /**
   * Checks parameter multiplicity rules for link target attributes.
   *
   * <p>
   * RFC 8288 defines multiplicity constraints for many parameters. This validator implements a
   * conservative rule:
   * </p>
   * <ul>
   *   <li>{@code hreflang} may appear more than once.</li>
   *   <li>All other parameters appearing more than once are reported.</li>
   * </ul>
   *
   * <p><strong>Important:</strong> The current implementation reports duplicates as {@code ERROR}.
   * RFC 8288 often specifies "occurrences after the first MUST be ignored by parsers".
   * </p>
   *
   * @param targetAttributes list of parameters on the {@link WebLink}
   * @param issueSink        a container with the recorded issues, the current index and the
   *                         recorded blocking weblinks
   */
  private static void validateTargetAttributeCardinality(List<WebLinkParameter> targetAttributes,
      IssueSink issueSink) {
    var attributeCounts = targetAttributes.stream().collect(
        Collectors.groupingBy(WebLinkParameter::name, Collectors.counting()));
    for (var entry : attributeCounts.entrySet()) {
      var attributeName = entry.getKey();
      var attributeCount = entry.getValue();
      if (attributeCount > 1 && !multipleOccurrencesAllowed(attributeName)) {
        issueSink.addError(
            "Multiple attribute definition available. Target attribute '%s' must not appear more than once for element at index %d".formatted(
                attributeName, issueSink.currentIndex));
      }
    }
  }

  /**
   * Returns whether a target attribute is allowed to occur multiple times.
   *
   * <p>
   * RFC 8288 allows multiple {@code hreflang}. Most other parameters are single-occurrence and are
   * treated as invalid.
   * </p>
   *
   * @param attributeName parameter name
   * @return true if repeated occurrence is allowed
   */
  private static boolean multipleOccurrencesAllowed(String attributeName) {
    return attributeName.equals("hreflang");
  }

  /**
   * Validates all parameter names of the given {@link WebLink}.
   *
   * <p>
   * Parameter names must conform to the RFC 7230 token grammar.
   * </p>
   *
   * @param currentLink link to inspect
   * @param issueSink   a container with the recorded issues, the current index and the recorded
   *                    blocking weblinks
   */
  private static void validateParameterNames(WebLink currentLink, IssueSink issueSink) {
    currentLink.params()
        .forEach(parameter -> validateParameterName(parameter.name(), issueSink));
  }

  /**
   * Validates a single parameter name for token compliance.
   *
   * @param name      parameter name
   * @param issueSink a container with the recorded issues, the current index and the recorded
   *                  blocking weblinks
   */
  private static void validateParameterName(String name, IssueSink issueSink) {
    if (!ALLOWED_TOKEN_CHARS.matcher(name).matches()) {
      issueSink.addError("Invalid parameter name '%s' for element at index %d".formatted(name,
          issueSink.currentIndex));
    }
  }

  /**
   * Ensures that the link has at least one relation type token.
   *
   * <p>
   * RFC 8288 requires the {@code rel} parameter to be present in the serialized representation. At
   * model level, we approximate this requirement by ensuring {@link WebLink#rel()} is non-empty.
   * </p>
   *
   * <p><strong>Maintainer note:</strong> This does not enforce "rel MUST NOT appear more than
   * once" because {@link WebLink#rel()} is a derived list of tokens. If strict "single rel
   * parameter" needs enforcement, validate based on {@link WebLink#params()} and count {@code rel}
   * parameters.</p>
   *
   * @param currentLink current link
   * @param issueSink   a container with the recorded issues, the current index and the recorded
   *                    blocking weblinks
   */
  private static void validateRelationPresence(WebLink currentLink, IssueSink issueSink) {
    if (currentLink.rel().isEmpty()) {
      issueSink.addError(
          "Missing relation parameter for element at index %d".formatted(issueSink.currentIndex));
    }
  }

  /**
   * Validates the link target URI ({@code <...>}) according to RFC 8288 model expectations.
   *
   * <h3>Normative basis</h3>
   * <p>
   * RFC 8288 defines the link target as a URI-reference in the serialized syntax. However, Compass
   * operates on a parsed <em>model</em> ({@link WebLink}) and applies the policy that any
   * URI-references must already be resolved at the parsing boundary. Therefore, a relative target
   * at model level is treated as an {@code ERROR}.
   * </p>
   *
   * <h3>Compass policy</h3>
   * <ul>
   *   <li>If the target URI is not absolute: {@code ERROR}.</li>
   *   <li>If the target URI scheme is not {@code http} or {@code https}: {@code WARNING}.</li>
   * </ul>
   *
   * <p>
   * The non-HTTP(S) case is not an RFC violation. It is a policy choice aligned with typical
   * FAIR Signposting deployments, where link targets are expected to be web-resolvable.
   * </p>
   *
   * @param targetUri the target URI of the link (from {@link WebLink#target()})
   * @param issueSink a container with the recorded issues, the current index and the recorded
   *                  blocking weblinks
   */
  private static void validateTargetUri(URI targetUri, IssueSink issueSink) {
    // Validates RFC 8288 Section 3.1 Link Target normative requirement for a target value
    if (!targetUri.isAbsolute()) {
      issueSink.addError(
          "Link target URI is relative for element at index %d".formatted(issueSink.currentIndex));
      return;
    }
    var scheme = targetUri.getScheme();
    if (!isHttpOrHttps(scheme)) {
      issueSink.addError(
          "Link target URI scheme is non-http for element at index %d: '%s'".formatted(
              issueSink.currentIndex, scheme));
    }
  }

  /**
   * Validates a single relation type token.
   *
   * <p>
   * A token is valid if it is either:
   * </p>
   * <ul>
   *   <li>a valid absolute URI (extension relation type), or</li>
   *   <li>a registered relation type token matching the RFC 8288 ABNF.</li>
   * </ul>
   *
   * <p>
   * {@code null}, empty, or whitespace-only tokens are reported as errors.
   * </p>
   */
  private static void validateRelationTypeToken(List<String> relationTypes, IssueSink issueSink) {
    for (var token : relationTypes) {
      validateRelationTypeToken(token, issueSink);
    }
  }

  /**
   * Validates a single relation type token extracted from {@link WebLink#rel()}.
   *
   * <p>
   * A token is considered valid if it is either:
   * </p>
   * <ul>
   *   <li>a valid absolute URI (extension relation type, {@code ext-rel-type}), or</li>
   *   <li>a registered relation type token matching {@code reg-rel-type} ABNF.</li>
   * </ul>
   *
   * <p>
   * This method intentionally does not enforce that the serialized {@code rel} parameter occurred
   * exactly once. That check must be performed on {@link WebLink#params()} if desired.
   * </p>
   *
   * @param typeToken relation type token (must not be {@code null} / blank)
   * @param issueSink a container with the recorded issues, the current index and the recorded
   *                  blocking weblinks
   */
  private static void validateRelationTypeToken(String typeToken, IssueSink issueSink) {
    if (typeToken == null) {
      issueSink.addError(
          "Relation type token is null for element at index %d".formatted(issueSink.currentIndex));
      return;
    }
    if (typeToken.isBlank()) {
      issueSink.addError(
          "Relation type token is invalid (reason: empty) for element at index %d".formatted(
              issueSink.currentIndex));
      return;
    }
    if (isValidUri(typeToken)) {
      return;
    }
    if (!REGULAR_RELATION_TYPE_PATTERN.matcher(typeToken).matches()) {
      issueSink.addError(
          "Relation type token contains invalid characters for element at index %d".formatted(
              issueSink.currentIndex));
    }
  }

  /**
   * Checks whether the provided string is a syntactically valid <em>absolute</em> URI.
   *
   * <p>
   * This is used to detect extension relation types ({@code ext-rel-type}) and anchor values.
   * Relative URIs are intentionally rejected here because Compass expects parsers to resolve
   * URI-references before constructing the model.
   * </p>
   *
   * @param value string to test
   * @return {@code true} if {@code value} parses as an absolute {@link URI}, otherwise
   * {@code false}
   */
  private static boolean isValidUri(String value) {
    try {
      var uri = URI.create(value);
      return uri.isAbsolute();
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  /**
   * Returns whether the provided URI scheme denotes HTTP(S).
   *
   * @param scheme the URI scheme string (must not be {@code null})
   * @return {@code true} if scheme is {@code "http"} or {@code "https"}
   */
  private static boolean isHttpOrHttps(String scheme) {
    return scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https");
  }

  static final class IssueSink {

    private final List<Issue> issues;
    private final boolean[] blockingLinkIndices;
    private int currentIndex;

    IssueSink(List<Issue> issues, boolean[] blockingLinkIndices, int currentIndex) {
      this.issues = Objects.requireNonNull(issues);
      this.blockingLinkIndices = Objects.requireNonNull(blockingLinkIndices);
      this.currentIndex = currentIndex;
    }

    void addWarning(String message) {
      addIssue(Issue.warning(message));
    }

    void addError(String message) {
      addIssue(Issue.error(message));
      blockingLinkIndices[currentIndex] = true;
    }

    private void addIssue(Issue issue) {
      issues.add(issue);
    }

  }
}
