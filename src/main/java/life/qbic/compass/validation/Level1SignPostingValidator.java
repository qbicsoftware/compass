package life.qbic.compass.validation;

import java.util.ArrayList;
import java.util.List;
import life.qbic.compass.spi.SignPostingResult;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.compass.model.SignPostingView;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Signposting validator for <strong>Level&nbsp;1</strong> of the FAIR Signposting profile.
 * <p>
 * Level&nbsp;1 Signposting describes the <em>minimal, inline</em> set of typed links that a
 * scholarly object should expose to enable reliable discovery, citation, attribution,
 * and access to descriptive metadata. These links are typically provided via HTTP
 * {@code Link} headers or HTML {@code <link>} elements and are directly attached to the
 * resource itself.
 * </p>
 *
 * <p>
 * This validator operates on a list of already parsed {@link WebLink}s and performs
 * structural and semantic checks according to the Level&nbsp;1 FAIR Signposting
 * recommendations:
 * </p>
 *
 * <ul>
 *   <li>
 *     <strong>{@code rel="author"}</strong> – recommended; a warning is raised if no author
 *     relation is present (cardinality 0..n).
 *   </li>
 *   <li>
 *     <strong>{@code rel="cite-as"}</strong> – mandatory; exactly one occurrence is expected,
 *     and an error is raised if missing or duplicated.
 *   </li>
 *   <li>
 *     <strong>{@code rel="describedby"}</strong> – mandatory; at least one occurrence is required
 *     to point to metadata describing the resource.
 *   </li>
 *   <li>
 *     <strong>Target URI scheme</strong> – all link targets are checked for secure transport;
 *     non-HTTPS or non-HTTP targets result in warnings.
 *   </li>
 * </ul>
 *
 * <p>
 * The validator is intentionally <strong>non-fatal</strong> where possible: it collects
 * errors and warnings for all detected issues instead of aborting on the first violation.
 * </p>
 *
 * <p>
 * This class does <strong>not</strong> dereference links, verify identifier persistence,
 * or validate the contents of metadata resources. Its responsibility is limited to
 * validating the presence, cardinality, and basic properties of Level&nbsp;1 Signposting
 * relations as they appear in the provided WebLinks.
 * </p>
 *
 * <p>
 * The resulting {@link SignPostingView} is a semantic convenience wrapper around the
 * original WebLinks; no links are filtered or modified during validation.
 * </p>
 *
 * @author Sven Fillinger
 */
public class Level1SignPostingValidator implements SignPostingValidator {

  private Level1SignPostingValidator() {
  }

  public static Level1SignPostingValidator create() {
    return new Level1SignPostingValidator();
  }

  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    List<Issue> issues = new ArrayList<>();

    // 1. the "author" relation
    validateAuthor(webLinks, issues);

    // 2. the "cite-as" relation
    validateCiteAs(webLinks, issues);

    // 3. the "describedBy" relation
    validateDescribedBy(webLinks, issues);

    // 4. report any unsecure http-only link and links that are not using the http scheme
    validateSecureHttp(webLinks, issues);

    return new SignPostingResult(new SignPostingView(webLinks), new IssueReport(issues));
  }

  private void validateSecureHttp(List<WebLink> webLinks, List<Issue> issues) {
    var unsafeLinks = webLinks.stream().filter(Level1SignPostingValidator::hasInsecureOrNoneHttp).toList();
    for (WebLink link : unsafeLinks) {
      issues.add(Issue.warning("Non-https link target found for relation type '%s': '%s'".formatted(
          link.rel(), link.target())));
    }
  }

  private void validateCiteAs(List<WebLink> webLinks, List<Issue> issues) {
    if (webLinks.stream().noneMatch(Level1SignPostingValidator::hasCiteAs)) {
      issues.add(Issue.error("Missing relation type 'cite-as'"));
      return;
    }

    if (webLinks.stream().filter(Level1SignPostingValidator::hasCiteAs).count() > 1) {
      issues.add(Issue.error("Multiple links for relation type 'cite-as' found"));
      return;
    }

    validateSecureHttp(
        webLinks.stream()
            .filter(Level1SignPostingValidator::hasCiteAs)
            .toList(),
        issues);
  }

  private void validateAuthor(List<WebLink> webLinks, List<Issue> recordedIssues) {
    var linksWithAuthorRel = webLinks.stream().anyMatch(Level1SignPostingValidator::hasAuthor);
    if (!linksWithAuthorRel) {
      recordedIssues.add(Issue.warning("Missing relation type 'author'"));
    }
  }

  private void validateDescribedBy(List<WebLink> webLinks, List<Issue> recordedIssues) {
    if (webLinks.stream().noneMatch(Level1SignPostingValidator::hasDescribedBy)) {
      recordedIssues.add(Issue.error("Missing relation type 'describedby'"));
    }
  }

  private static boolean hasAny(WebLink weblink, String relation) {
    return weblink.rel().stream().anyMatch(relation::equals);
  }

  private static boolean hasAuthor(WebLink webLink) {
    return hasAny(webLink, "author");
  }

  private static boolean hasCiteAs(WebLink webLink) {
    return hasAny(webLink, "cite-as");
  }

  private static boolean hasDescribedBy(WebLink webLink) {
    return hasAny(webLink, "describedby");
  }

  private static boolean hasInsecureOrNoneHttp(WebLink webLink) {
    return !hasSecureHttp(webLink);
  }

  private static boolean hasSecureHttp(WebLink webLink) {
    return webLink.target().getScheme().equalsIgnoreCase("https");
  }
}
