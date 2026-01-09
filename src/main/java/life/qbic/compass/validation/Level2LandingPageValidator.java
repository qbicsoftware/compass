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
import life.qbic.linksmith.spi.WebLinkValidator.IssueType;

/**
 * Validates a FAIR Signposting Level 2 <em>Landing Page</em> recipe against a list of
 * {@link WebLink}s.
 *
 * <p>
 * This validator assumes the input links originate from a Level 2 Link Set interpretation where
 * each {@link WebLink} may carry an {@code anchor} attribute that represents the <em>link
 * context</em> (i.e., the origin resource to which the typed link applies).
 * </p>
 *
 * <h2>Scope</h2>
 * <p>
 * The validator checks whether the provided collection can be interpreted as a single landing page
 * context and whether mandatory/optional relation types satisfy the expected cardinalities for the
 * landing page recipe.
 * </p>
 *
 * <h2>Single-origin requirement</h2>
 * <p>
 * The validator does <strong>not</strong> assume that the input has been grouped by {@code anchor}.
 * Therefore:
 * </p>
 * <ul>
 *   <li>If any {@link WebLink} has a missing {@code anchor} value, an {@link IssueType#ERROR} is recorded for its link target and recipe validation is aborted early. No cardinality validation will be done in this case.</li>
 *   <li>If multiple distinct {@code anchor} values are present, an {@link IssueType#ERROR} is recorded and
 *       recipe validation is aborted early (because completeness cannot be determined reliably). No cardinality validation will be done in this case.</li>
 * </ul>
 *
 * <h2>Relations validated</h2>
 * <p>
 * The validator counts relation occurrences using {@link WebLink#rel()} and validates required relations
 * and cardinalities:
 * </p>
 * <ul>
 *   <li>{@code cite-as}: mandatory, cardinality (1)</li>
 *   <li>{@code describedby}: mandatory, cardinality (1..n) (only presence is checked)</li>
 *   <li>{@code item}: mandatory, cardinality (1..n) (only presence is checked)</li>
 *   <li>{@code type}: mandatory, cardinality (1,2)</li>
 *   <li>{@code license}: optional, cardinality (0,1)</li>
 * </ul>
 *
 * <p>
 * The produced {@link SignPostingResult} always contains a {@link SignPostingView} over the original
 * input links (non-destructive) and a report with all recorded issues.
 * </p>
 *
 * @author Sven Fillinger
 * @since 1.0.0
 */
public class Level2LandingPageValidator implements SignPostingValidator {

  /**
   * Relation type {@code cite-as}.
   */
  public static final String CITE_AS = "cite-as";

  /**
   * Relation type {@code describedby}.
   */
  public static final String DESCRIBEDBY = "describedby";

  /**
   * Relation type {@code item}.
   */
  public static final String ITEM = "item";

  /**
   * Relation type {@code type}.
   */
  public static final String TYPE = "type";

  /**
   * Private constructor. Use {@link #create()} to obtain an instance.
   */
  private Level2LandingPageValidator() {
  }

  /**
   * Creates a new validator instance.
   *
   * <p>
   * The returned instance is stateless and can be reused.
   * </p>
   *
   * @return a {@link SignPostingValidator} that validates the Level 2 Landing Page recipe
   */
  public static SignPostingValidator create() {
    return new Level2LandingPageValidator();
  }

  /**
   * Validate the given list of {@link WebLink}s against the Level 2 Landing Page recipe.
   *
   * <p>
   * The validation collects issues rather than failing fast (except for the ambiguous multi-anchor
   * case where further recipe validation is not meaningful).
   * </p>
   *
   * @param webLinks the web links to validate; the list is treated as read-only
   * @return a {@link SignPostingResult} containing a {@link SignPostingView} over the input links
   * and an {@link IssueReport} describing any detected violations
   */
  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    Objects.requireNonNull(webLinks);
    var issues = new ArrayList<Issue>();
    validateForLandingPage(webLinks, issues);

    return new SignPostingResult(new SignPostingView(webLinks), new IssueReport(issues));
  }

  /**
   * Performs Landing Page recipe validation.
   *
   * <p>
   * This method enforces the single-origin requirement: it selects the first encountered non-null
   * {@code anchor} as the expected origin and rejects any subsequent link with a different
   * {@code anchor}.
   * </p>
   *
   * <p>
   * During iteration, relation types are counted using {@link WebLink#rel()}, but only for links
   * that match the selected anchor. Links with missing anchors are recorded and reported after the
   * scan.
   * </p>
   *
   * <p>
   * If multiple different anchors are found, an error is recorded and the method returns early
   * because it cannot reliably determine whether the Landing Page recipe is complete for any single
   * origin.
   * </p>
   *
   * @param webLinks the input links to validate
   * @param issues   the mutable list used to record validation issues
   */
  private void validateForLandingPage(List<WebLink> webLinks, List<Issue> issues) {
    var linksWithoutAnchor = new ArrayList<WebLink>();
    var recordedRelations = new HashMap<String, Integer>();

    // Validates for a unique context (anchor)
    var isContextUnique = Level2Util.validateForSingleAnchor(webLinks, issues, linksWithoutAnchor,
        recordedRelations);
    if (!isContextUnique) {
      return;
    }

    // Validate and record issues for links without anchors
    // Missing anchors violate Level 2 FAIR Signposting recipes, since the origin of the link
    // cannot be determined
    if (!linksWithoutAnchor.isEmpty()) {
      linksWithoutAnchor.forEach(link -> issues.add(Issue.error(
          "Found weblink with missing value for 'anchor'. Link target was '%s'".formatted(
              link.target()))));
      return;
    }

    validateCiteAs(recordedRelations, issues);
    validateDescribedBy(recordedRelations, issues);
    validateItem(recordedRelations, issues);
    validateLicense(recordedRelations, issues);
    validateType(recordedRelations, issues);
  }

  /**
   * Validates the {@code type} relation for the landing page recipe.
   *
   * <p>
   * The landing page must have at least one {@code type} link and no more than two.
   * </p>
   *
   * @param recordedRelations relation counts collected for the selected anchor context
   * @param issues            the list used to record validation issues
   */
  private static void validateType(Map<String, Integer> recordedRelations, List<Issue> issues) {
    validatePresenceOfMandatoryRelation(recordedRelations, issues, TYPE);
    var count = recordedRelations.getOrDefault(TYPE, 0);
    if (count > 2) {
      issues.add(Issue.error(
          "Too many links with relation type 'type' found (%d). Expected a cardinality of (1,2)"
              .formatted(count)));
    }
  }

  /**
   * Validates presence of the {@code item} relation.
   *
   * <p>
   * The landing page recipe requires at least one {@code item} link. This method currently checks
   * presence only (cardinality (1..n)).
   * </p>
   *
   * @param recordedRelations relation counts collected for the selected anchor context
   * @param issues            the list used to record validation issues
   */
  private static void validateItem(Map<String, Integer> recordedRelations, List<Issue> issues) {
    validatePresenceOfMandatoryRelation(recordedRelations, issues, ITEM);
  }

  /**
   * Validates presence of the {@code describedby} relation.
   *
   * <p>
   * The landing page recipe requires at least one {@code describedby} link. This method currently
   * checks presence only (cardinality (1..n)).
   * </p>
   *
   * @param recordedRelations relation counts collected for the selected anchor context
   * @param issues            the list used to record validation issues
   */
  private static void validateDescribedBy(Map<String, Integer> recordedRelations,
      List<Issue> issues) {
    validatePresenceOfMandatoryRelation(recordedRelations, issues, DESCRIBEDBY);
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

  /**
   * Validates the {@code cite-as} relation.
   *
   * <p>
   * The landing page recipe requires exactly one {@code cite-as} link (cardinality (1)).
   * </p>
   *
   * @param recordedRelations relation counts collected for the selected anchor context
   * @param issues            the list used to record validation issues
   */
  private static void validateCiteAs(Map<String, Integer> recordedRelations,
      List<Issue> issues) {
    validatePresenceOfMandatoryRelation(recordedRelations, issues, CITE_AS);
    var count = recordedRelations.getOrDefault(CITE_AS, 0);
    if (count > 1) {
      issues.add(Issue.error(
          "Multiple links with relation type 'cite-as' found (%d). Expected a cardinality of (1)"
              .formatted(count)));
    }
  }

  /**
   * Validates the {@code license} relation.
   *
   * <p>
   * The landing page recipe allows the {@code license} relation to be omitted, but if present it
   * must occur at most once (cardinality (0,1)).
   * </p>
   *
   * @param recordedRelations relation counts collected for the selected anchor context
   * @param issues            the list used to record validation issues
   */
  private static void validateLicense(Map<String, Integer> recordedRelations,
      List<Issue> issues) {
    var count = recordedRelations.getOrDefault("license", 0);
    if (count > 1) {
      issues.add(Issue.error(
          "Multiple links with relation type 'license' are not allowed (%d). Expected a cardinality of (0,1)"
              .formatted(count)));
    }
  }
}
