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

    // 1. Validate availability of typed 'linkset' web link
    validateLinkSet(webLinks, issues);

    return new SignPostingResult(new SignPostingView(webLinks), new IssueReport(issues));
  }

  /**
   * According to the FAIR Signposting level 2 profile specification, a linkset must be made
   * discoverable with the <a
   * href="https://www.iana.org/assignments/link-relations/link-relations.xhtml">IANA link relation
   * attribute</a> {@code rel=linkset} and the target media type one of
   * {@code type=application/linkset} or {@code type=application/linkset+json} must be available.
   *
   * @param webLinks a list of weblink to validate for the presence of a linkset typed link
   * @param issues   the running recorded issues
   */
  private void validateLinkSet(List<WebLink> webLinks, List<Issue> issues) {

    var weblinksWithLinkSetRel = webLinks.stream()
        .filter(Level2DiscoveryValidator::hasLinkSetRelation)
        .toList();

    if (weblinksWithLinkSetRel.isEmpty()) {
      issues.add(Issue.error("No resource with 'rel=linkset' was found"));
      return;
    }

    if (weblinksWithLinkSetRel.stream()
        .noneMatch(Level2DiscoveryValidator::hasType)) {
      issues.add(Issue.warning("Missing type for linkset"));
    }

    var compliantLinkSet = new ArrayList<WebLink>();

    weblinksWithLinkSetRel.forEach(link -> {
      if (!hasSupportedLinkSetType(link)) {
        issues.add(Issue.warning("Unsupported type '%s' for linkset".formatted(link.type())));
        return;
      }
      compliantLinkSet.add(link);
    });

    // We want to warn, in case there are several linkset resources linked, and they
    // have different target locations.
    var distinctLinkSetTargets = compliantLinkSet.stream()
        .map(WebLink::target)
        .distinct()
        .toList();
    if (distinctLinkSetTargets.size() > 1) {
      issues.add(Issue.warning("Linkset has multiple targets"));
    }

    if (weblinksWithLinkSetRel.stream()
        .noneMatch(Level2DiscoveryValidator::hasSupportedLinkSetType)) {
      issues.add(Issue.error(
          "No supported linkset type found"));
    }
  }

  private static boolean hasLinkSetRelation(WebLink webLink) {
    return webLink.rel().stream().anyMatch(Level2DiscoveryValidator::hasLinkSetRelation);
  }

  private static boolean hasLinkSetRelation(String relation) {
    return relation.equals("linkset");
  }

  private static boolean hasType(WebLink webLink) {
    return webLink.type().isPresent();
  }

  private static boolean hasSupportedLinkSetType(WebLink webLink) {
    return hasSupportedLinkSetType(webLink.type().orElse(""));
  }

  private static boolean hasSupportedLinkSetType(String type) {
    return type.equalsIgnoreCase("application/linkset")
        || type.equalsIgnoreCase("application/linkset+json");
  }
}
