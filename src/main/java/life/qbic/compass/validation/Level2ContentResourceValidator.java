package life.qbic.compass.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import life.qbic.compass.model.SignPostingView;
import life.qbic.compass.spi.SignPostingResult;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public class Level2ContentResourceValidator implements SignPostingValidator {

  public static final String CITE_AS = "cite-as";
  public static final String COLLECTION = "collection";
  public static final String LICENSE = "license";
  public static final String TYPE = "type";

  private Level2ContentResourceValidator() {
  }

  public static SignPostingValidator create() {
    return new Level2ContentResourceValidator();
  }

  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    Objects.requireNonNull(webLinks);
    var issues = new ArrayList<Issue>();
    validateForContentResource(webLinks, issues);

    return new SignPostingResult(
        new SignPostingView(
            webLinks.stream()
                .filter(Objects::nonNull)
                .toList()),
        new IssueReport(issues));
  }

  private void validateForContentResource(List<WebLink> webLinks, ArrayList<Issue> issues) {
    var linksWithoutAnchor = new ArrayList<WebLink>();
    var recordedRelations = new HashMap<String, Integer>();

    var isContextUnique = Level2Util.validateForSingleAnchor(webLinks, issues, linksWithoutAnchor,
        recordedRelations);

    if (!isContextUnique) {
      // Early abort: context ambiguity (e.g. multiple distinct anchors) prevents reliable recipe validation.
      return;
    }

    // Validate and record issues for links without anchors
    // Missing anchors violate Level 2 FAIR Signposting recipes, since the origin of the link
    // cannot be determined
    if (!linksWithoutAnchor.isEmpty()) {
      linksWithoutAnchor.forEach(link -> issues.add(Issue.error(
          "Found weblink with missing value for 'anchor'. Link target was '%s'".formatted(
              link.target()))));
      // Early abort without further cardinality evaluation
      return;
    }

    // Validate for correct cardinality
    // relation 'cite-as' cardinality is expected to be 0 or 1
    validateCiteAs(recordedRelations, issues);
    // relation 'collection' cardinality is expected to be exactly 1
    validateCollection(recordedRelations, issues);
    validateLicense(recordedRelations, issues);
    validateType(recordedRelations, issues);
  }

  private void validateType(HashMap<String, Integer> recordedRelations, ArrayList<Issue> issues) {
    var count = recordedRelations.getOrDefault(TYPE, 0);
    if (count > 1) {
      issues.add(Issue.error(
          "Multiple weblinks with relation '%s' (%d) found. Expected cardinality: (0..1)".formatted(TYPE,
              count)));
    }
  }

  private void validateLicense(HashMap<String, Integer> recordedRelations,
      ArrayList<Issue> issues) {
    var count = recordedRelations.getOrDefault(LICENSE, 0);
    if (count > 1) {
      issues.add(Issue.error(
          "Multiple weblinks with relation '%s' (%d) found. Expected cardinality: (0..1)".formatted(LICENSE,
              count)));
    }
  }

  private void validateCollection(HashMap<String, Integer> recordedRelations,
      ArrayList<Issue> issues) {
    validatePresenceOfMandatoryRelation(recordedRelations, issues, COLLECTION);
    var count = recordedRelations.getOrDefault(COLLECTION, 0);
    if (count > 1) {
      issues.add(Issue.error(
          "Multiple weblinks with relation '%s' (%d) found. Expected cardinality: (1)".formatted(COLLECTION,
              count)));
    }
  }

  private static void validateCiteAs(Map<String, Integer> recordedRelations, List<Issue> issues) {
    var count = recordedRelations.getOrDefault(CITE_AS, 0);
    if (count > 1) {
      issues.add(Issue.error(
          "Multiple weblinks with relation '%s' (%d) found. Expected cardinality: (0..1)".formatted(CITE_AS,
              count)));
    }
  }

  /**
   * Records an error if a mandatory relation is missing.
   *
   * <p>
   * Presence is defined by the existence of the relation key in {@code recordedRelations}.
   * </p>
   *
   * @param recordedRelations relation counts collected for the selected anchor context
   * @param issues            the list used to record validation issues
   * @param relation          the relation type to validate
   */
  private static void validatePresenceOfMandatoryRelation(
      Map<String, Integer> recordedRelations,
      List<Issue> issues, String relation) {
    if (!recordedRelations.containsKey(relation)) {
      issues.add(Issue.error("Missing mandatory relation type '%s'".formatted(relation)));
    }
  }
}
