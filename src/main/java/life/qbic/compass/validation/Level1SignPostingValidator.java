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
 * Validator for the FAIR Signposting <strong>Level&nbsp;1</strong> profile.
 *
 * <h2>Scope and normative target</h2>
 * <p>
 * FAIR Signposting Level&nbsp;1 defines a <em>minimal</em> set of typed links intended to support
 * robust machine navigation of scholarly objects. Importantly, in Level&nbsp;1 only the
 * <strong>landing page recipe</strong> is <strong>mandatory</strong> and therefore
 * <strong>normative</strong>.
 * </p>
 *
 * <p>
 * Typed links exposed on <em>content resources</em> and <em>metadata resources</em> are
 * <strong>recommended</strong> in Level&nbsp;1 but not required by the profile. Consequently, this
 * validator deliberately does <strong>not</strong> attempt to infer, validate, or enforce completeness
 * of Level&nbsp;1 content/metadata resource recipes. It validates only the Level&nbsp;1
 * <strong>landing page</strong> expectations on the provided links.
 * </p>
 *
 * <h2>What this validator checks</h2>
 * <p>
 * The validator operates on a list of already parsed {@link WebLink}s (e.g., obtained from HTTP
 * {@code Link} headers or HTML {@code <link>} elements) and records issues according to the
 * Level&nbsp;1 landing page recipe:
 * </p>
 *
 * <ul>
 *   <li>
 *     <strong>{@code rel="cite-as"}</strong> – <strong>mandatory</strong> for the landing page;
 *     exactly one link is expected (error if missing or duplicated).
 *   </li>
 *   <li>
 *     <strong>{@code rel="describedby"}</strong> – <strong>mandatory</strong> for the landing page;
 *     at least one link is expected (error if missing).
 *   </li>
 *   <li>
 *     <strong>{@code rel="author"}</strong> – <strong>recommended</strong> for the landing page;
 *     a warning is recorded if absent (cardinality 0..n).
 *   </li>
 *   <li>
 *     <strong>Transport security</strong> – emits warnings for link targets that are not HTTPS.
 *   </li>
 * </ul>
 *
 * <h2>Non-goals</h2>
 * <ul>
 *   <li>No dereferencing of link targets (no network access).</li>
 *   <li>No validation of metadata payloads or identifier persistence.</li>
 *   <li>No enforcement of Level&nbsp;1 recommendations for content/metadata resources.</li>
 * </ul>
 *
 * <p>
 * The returned {@link SignPostingView} is a semantic, read-only convenience wrapper around the
 * original WebLinks. Validation issues are reported via {@link IssueReport}; the view itself does
 * not modify or filter links.
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
