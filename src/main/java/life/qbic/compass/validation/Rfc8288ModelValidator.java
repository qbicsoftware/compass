package life.qbic.compass.validation;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * <class short description>
 *
 * @since <version tag>
 */
class Rfc8288ModelValidator implements WebLinkModelValidator {

  /**
   * Following the ABNF for regular "rel" parameters' value:
   *
   * <pre>{@code
   *     relation-type  = reg-rel-type / ext-rel-type
   *     reg-rel-type   = LOALPHA *( LOALPHA / DIGIT / "." / "-" )
   * }</pre>
   * <p>
   * the constant captures the notation rule as a regular expression.
   * <p>
   * The pattern compiles and matches case-insensitive, since the semantics of the specification
   * give away (see section 2.1.1, that for registered link relation types, they MUST be
   * <strong>compared</strong> character by character in a case-insensitive
   * fashion.
   */
  private static final Pattern REGULAR_RELATION_TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9.-]*$",
      Pattern.CASE_INSENSITIVE);

  // Defined in https://www.rfc-editor.org/rfc/rfc7230, section 3.2.6
  private static final Pattern ALLOWED_TOKEN_CHARS = Pattern.compile(
      "^[!#$%&'*+-.^_`|~0-9A-Za-z]+$");

  static Rfc8288ModelValidator create() {
    return new Rfc8288ModelValidator();
  }

  @Override
  public IssueReport validate(List<WebLink> webLinks) {
    // Throws NPE early
    Objects.requireNonNull(webLinks);

    var issues = new ArrayList<Issue>();
    for (int index = 0; index < webLinks.size(); index++) {
      var currentLink = webLinks.get(index);
      if (currentLink == null) {
        issues.add(Issue.error("Element is null at index %d".formatted(index)));
        continue;
      }
      validate(currentLink, index, issues);
    }
    return new IssueReport(issues);
  }

  private static void validate(WebLink currentLink, int index, List<Issue> issues) {
    validateTargetUri(currentLink.target(), index, issues);
    validateRelationPresence(currentLink, index, issues);
    validateRelationTypeToken(currentLink.rel(), index, issues);
    validateParameterNames(currentLink, index, issues);
  }

  private static void validateParameterNames(WebLink currentLink, int index, List<Issue> issues) {
    currentLink.params().forEach(parameter -> validateParameterName(parameter.name(), index, issues));
  }

  private static void validateParameterName(String name, int index, List<Issue> issues) {
    if (!ALLOWED_TOKEN_CHARS.matcher(name).matches()) {
      issues.add(Issue.error("Invalid parameter name '%s' for element at index %d".formatted(name, index)));
    }
  }

  private static void validateRelationPresence(WebLink currentLink, int index, List<Issue> issues) {
    if (currentLink.rel().isEmpty()) {
      issues.add(
          Issue.error("Missing relation parameter for element at index %d".formatted(index)));
    }
  }

  private static void validateTargetUri(URI targetUri, int index, List<Issue> issues) {
    // Validates RFC 8288 Section 3.1 Link Target normative requirement for a target value
    if (!targetUri.isAbsolute()) {
      issues.add(
          Issue.error("Link target URI is relative for element at index %d".formatted(index)));
      return;
    }
    var scheme = targetUri.getScheme();
    if (!isHttpOrHttps(scheme)) {
      issues.add(Issue.warning(
          "Link target URI scheme is non-http for element at index %d: '%s'".formatted(index,
              scheme)));
    }
  }

  private static void validateRelationTypeToken(List<String> relationTypes, int index,
      List<Issue> issues) {
    for (var token : relationTypes) {
      validateRelationTypeToken(token, index, issues);
    }
  }

  private static void validateRelationTypeToken(String typeToken, int index, List<Issue> issues) {
    if (typeToken == null) {
      issues.add(
          Issue.error("Relation type token is null for element at index %d".formatted(index)));
      return;
    }
    if (typeToken.isBlank()) {
      issues.add(Issue.error(
          "Relation type token is invalid (reason: empty) for element at index %d".formatted(
              index)));
      return;
    }
    if (isValidUri(typeToken)) {
      return;
    }
    if (!REGULAR_RELATION_TYPE_PATTERN.matcher(typeToken).matches()) {
      issues.add(Issue.error(
          "Relation type token contains invalid characters for element at index %d".formatted(
              index)));
    }
  }

  private static boolean isValidUri(String value) {
    try {
      var uri = URI.create(value);
      return uri.isAbsolute();
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private static boolean isHttpOrHttps(String scheme) {
    return scheme.equals("http") || scheme.equals("https");
  }
}
