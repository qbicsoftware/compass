package life.qbic.jsign.validator;

import java.util.ArrayList;
import java.util.List;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * <class short description>
 *
 * @since <version tag>
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

    validateUnsafeHttp(webLinks, issues);

    return new SignPostingResult(new SignPostingView(webLinks), new IssueReport(issues));
  }

  private void validateUnsafeHttp(List<WebLink> webLinks, List<Issue> issues) {
    var unsafeLinks = webLinks.stream().filter(Level1SignPostingValidator::hasUnsafeHttp).toList();
    for (WebLink link : unsafeLinks) {
      issues.add(Issue.warning("Unsafe HTTP target found for relation type '%s': '%s'".formatted(
          link.rel(), link.reference())));
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

    validateUnsafeHttp(
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

  private static boolean hasUnsafeHttp(WebLink webLink) {
    return !webLink.reference().getScheme().equalsIgnoreCase("http");
  }
}
