package life.qbic.jsign.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Signposting validator for <strong>Level&nbsp;2 discovery</strong>.
 * <p>
 * Level&nbsp;1 Signposting exposes typed links for a scholarly object
 * <em>inline</em>, for example in HTTP {@code Link} headers or HTML
 * {@code <link>} elements (e.g. {@code rel="cite-as"}, {@code rel="describedby"},
 * {@code rel="item"}).
 * <br>
 * Level&nbsp;2 Signposting, in contrast, exposes (the same and additional) typed
 * links <em>by reference</em> through a separate <em>Linkset</em> resource, which
 * is advertised from the object using {@code rel="linkset"} and a dedicated
 * media type such as {@code application/linkset} or {@code application/linkset+json}.
 * </p>
 *
 * <p>
 * This validator focuses <strong>only on discovering</strong> Level&nbsp;2
 * Linkset resources in an already parsed set of {@link WebLink}s. It:
 * </p>
 * <ul>
 *   <li>looks for links with {@code rel="linkset"},</li>
 *   <li>checks whether a supported Linkset media type
 *       ({@code application/linkset} or {@code application/linkset+json}) is present,</li>
 *   <li>records warnings for missing or unsupported {@code type} parameters, and</li>
 *   <li>records a warning if multiple supported Linkset targets are advertised.</li>
 * </ul>
 *
 * <p>
 * The validator <strong>does not</strong> dereference Linkset URIs, perform any
 * network I/O, or validate the contents of a Linkset document. It only tells you
 * whether a suitable Linkset entry is discoverable from the given WebLinks.
 * </p>
 *
 * <p>
 * Downstream, clients are expected to:
 * </p>
 * <ol>
 *   <li>Inspect the {@link SignPostingResult} / {@link IssueReport} to ensure a
 *       supported Linkset was discovered,</li>
 *   <li>select one of the advertised Linkset targets and fetch it over HTTP, and</li>
 *   <li>pass the fetched Linkset content (e.g. {@code application/linkset+json})
 *       to a dedicated Linkset parser and Level&nbsp;2 profile validator that operate
 *       on the Linkset’s WebLinks.</li>
 * </ol>
 *
 * <p>
 * The {@link SignPostingView} returned in the {@link SignPostingResult} simply
 * wraps the input WebLinks and does not modify or filter them; all discovery
 * information is conveyed via the recorded {@link Issue}s.
 * </p>
 *
 * @author Sven Fillinger
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

  /**
   * Verifies if a given web link contains a relation of type {@code linkset}.
   *
   * @param webLink the web link to verify
   * @return true, if the weblink has a relation of type {@code linkset}, else returns false
   */
  private static boolean hasLinkSetRelation(WebLink webLink) {
    return webLink.rel().stream().anyMatch(Level2DiscoveryValidator::hasLinkSetRelation);
  }

  /**
   * Verifies if a given serialised relation type is `linkset`.
   *
   * @param relation the potential serialised linkset type
   * @return true, if the linkset type is 'linkset', else returns false
   */
  private static boolean hasLinkSetRelation(String relation) {
    return relation.equals("linkset");
  }

  /**
   * Verifies if a given web link has the type parameter.
   *
   * @param webLink the weblink to verify
   * @return true, if the web link has the type parameter, else returns false
   */
  private static boolean hasType(WebLink webLink) {
    return webLink.type().isPresent();
  }

  /**
   * Verifies if a given web link has any supported MIME type for a linkset.
   * <p>
   * Note: this method will also consider the case of absent type parameter, for which it will
   * always return false.
   *
   * @param webLink the web link to verify for any supported MIME type for linksets
   * @return true, if the web link contains any supported linkset MIME type, else returns false
   */
  private static boolean hasSupportedLinkSetType(WebLink webLink) {
    return hasSupportedLinkSetType(webLink.type().orElse(""));
  }

  /**
   * Verifies if a given serialised type is a supported MIME type for linksets.
   *
   * @param type the serialised MIME type to verify
   * @return true, if the type is a known MIME type for linksets
   */
  private static boolean hasSupportedLinkSetType(String type) {
    return type.equalsIgnoreCase("application/linkset")
        || type.equalsIgnoreCase("application/linkset+json");
  }
}
