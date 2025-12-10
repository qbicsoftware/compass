package life.qbic.jsign.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class Level2DiscoveryValidator implements SignPostingValidator {

  private Level2DiscoveryValidator() {
  }

  public static Level2DiscoveryValidator create() {
    return new Level2DiscoveryValidator();
  }

  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    Objects.requireNonNull(webLinks);
    ArrayList<Issue> issues = new ArrayList<>();

    // 1. Validate availability of types 'linkset' and/or 'linkset+json'
    validateLinkSet(webLinks, issues);

    return new SignPostingResult(new SignPostingView(webLinks), new IssueReport(issues));
  }

  private void validateLinkSet(List<WebLink> webLinks, List<Issue> issues) {
    var weblinksWithLinkSetRel = webLinks.stream().filter(Level2DiscoveryValidator::hasLinkSetRelation).toList();
    if (webLinks.stream().noneMatch(Level2DiscoveryValidator::hasAnyLinkSetType)) {
      issues.add(Issue.error("No linkset resource found. Expect at least one link of type 'application/linkset' or 'application/linkset+json'"));
    }
  }

  private static boolean hasLinkSetRelation(WebLink webLink) {
    return webLink.rel().stream().anyMatch(Level2DiscoveryValidator::hasLinkSetRelation);
  }

  private static boolean hasLinkSetRelation(String relation) {
    return relation.equals("linkset");
  }

  private static boolean hasAnyLinkSetType(WebLink webLink) {
    return hasAnyLinkSetType(webLink.type().orElse(""));
  }

  private static boolean hasAnyLinkSetType(String type) {
    return type.equalsIgnoreCase("application/linkset") || type.equalsIgnoreCase("application/linkset+json");
  }
}
