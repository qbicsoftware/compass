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
 * Validates the FAIR Signposting <em>Level 2</em> <strong>content resource recipe</strong>
 * for a single content resource context.
 *
 * <p>
 * In FAIR Signposting Level 2, typed links for multiple resources are typically conveyed via a
 * Link Set (e.g., {@code application/linkset+json}). A content resource is one of the resources
 * in that graph (e.g., a PDF, CSV, ZIP, etc.). This validator checks whether a given list of
 * {@link WebLink}s satisfies the expected relation set and cardinalities for the content resource
 * context.
 * </p>
 *
 * <h2>How this validator determines the context</h2>
 * <p>
 * This validator does <strong>not</strong> assume the input is pre-grouped by anchor.
 * It uses {@link Level2Util#validateForSingleAnchor(List, List, List, Map)} to ensure that all
 * links in the input belong to exactly one anchor (origin) value.
 * </p>
 *
 * <ul>
 *   <li>If multiple distinct anchors are present, the context is ambiguous and recipe validation is aborted.</li>
 *   <li>If one or more links are missing an {@code anchor} value, recipe validation is aborted and errors are recorded.</li>
 * </ul>
 *
 * <h2>Null handling contract</h2>
 * <p>
 * The input list itself must not be {@code null}. If {@code null} elements are contained in the
 * list, they are:</p>
 * <ul>
 *   <li>passed to {@link Level2Util#validateForSingleAnchor(List, List, List, Map)} which is expected to record issues, and</li>
 *   <li>filtered out when constructing the {@link SignPostingView} to prevent {@link NullPointerException}
 *       and to keep the result consumable for clients.</li>
 * </ul>
 *
 * <h2>Validated relations and cardinalities</h2>
 * <p>
 * For the content resource recipe, this validator checks the following relations (as expressed in
 * {@code rel=} parameters):
 * </p>
 * <ul>
 *   <li>{@code collection}: mandatory, cardinality {@code (1)}</li>
 *   <li>{@code cite-as}: optional, cardinality {@code (0..1)}</li>
 *   <li>{@code license}: optional, cardinality {@code (0..1)}</li>
 *   <li>{@code type}: optional, cardinality {@code (0..1)}</li>
 * </ul>
 *
 * <p>
 * This validator does not dereference targets, does not validate media types, and does not validate
 * URI semantics beyond what the upstream Link parsing/validation already guarantees.
 * </p>
 *
 * @since 1.0.0
 * @author Sven Fillinger
 */
public class Level2ContentResourceValidator implements SignPostingValidator {

  /** Relation type {@code cite-as} used by FAIR Signposting. */
  public static final String CITE_AS = "cite-as";

  /** Relation type {@code collection} used by FAIR Signposting for content resources. */
  public static final String COLLECTION = "collection";

  /** Relation type {@code license} used by FAIR Signposting. */
  public static final String LICENSE = "license";

  /** Relation type {@code type} used by FAIR Signposting (semantic typing). */
  public static final String TYPE = "type";

  /**
   * Private constructor. Use {@link #create()}.
   */
  private Level2ContentResourceValidator() {
  }

  /**
   * Creates a new validator instance.
   *
   * <p>
   * The validator is stateless; instances can be reused.
   * </p>
   *
   * @return a new {@link SignPostingValidator} validating the Level 2 content resource recipe
   */
  public static SignPostingValidator create() {
    return new Level2ContentResourceValidator();
  }

  /**
   * Validates the provided list of {@link WebLink}s against the FAIR Signposting Level 2 content
   * resource recipe.
   *
   * <p>
   * The returned {@link SignPostingResult} always contains a {@link SignPostingView}. The view is
   * built from a filtered copy of the input list with all {@code null} elements removed.
   * </p>
   *
   * <p>
   * Validation is best-effort and issue-driven:
   * </p>
   * <ul>
   *   <li>If the input contains multiple distinct anchor values, validation aborts after recording an error.</li>
   *   <li>If the input contains links without an {@code anchor} value, validation aborts after recording errors.</li>
   *   <li>Otherwise, relation cardinalities are evaluated and corresponding issues are recorded.</li>
   * </ul>
   *
   * @param webLinks the weblinks to validate; must not be {@code null}
   * @return the validation result containing a view and an issue report
   * @throws NullPointerException if {@code webLinks} is {@code null}
   */
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

  /**
   * Performs the actual recipe validation for a content resource context.
   *
   * <p>
   * The method first checks whether all links belong to exactly one anchor (origin) context.
   * If the context is ambiguous (multiple anchors) or incomplete (missing anchors), the method
   * records issues and returns early without evaluating relation cardinalities.
   * </p>
   *
   * @param webLinks the input list of links (may contain {@code null} elements)
   * @param issues   the mutable list used to record validation issues
   */
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
    // Validate relation cardinalities for the content resource recipe.
    validateCiteAs(recordedRelations, issues);      // (0..1)
    validateCollection(recordedRelations, issues);  // (1)
    validateLicense(recordedRelations, issues);     // (0..1)
    validateType(recordedRelations, issues);        // (0..1)
  }

  /**
   * Validates the cardinality of relation {@code type}.
   *
   * <p>
   * For the content resource recipe, {@code type} is optional but must not occur more than once
   * within the selected anchor context.
   * </p>
   *
   * @param recordedRelations map of relation type to occurrence count for the selected anchor context
   * @param issues            list used to record validation issues
   */
  private static void validateType(HashMap<String, Integer> recordedRelations, ArrayList<Issue> issues) {
    var count = recordedRelations.getOrDefault(TYPE, 0);
    if (count > 1) {
      issues.add(Issue.error(
          "Multiple weblinks with relation '%s' (%d) found. Expected cardinality: (0..1)".formatted(TYPE,
              count)));
    }
  }

  /**
   * Validates the cardinality of relation {@code license}.
   *
   * <p>
   * For the content resource recipe, {@code license} is optional but must not occur more than once
   * within the selected anchor context.
   * </p>
   *
   * @param recordedRelations map of relation type to occurrence count for the selected anchor context
   * @param issues            list used to record validation issues
   */
  private static void validateLicense(HashMap<String, Integer> recordedRelations,
      ArrayList<Issue> issues) {
    var count = recordedRelations.getOrDefault(LICENSE, 0);
    if (count > 1) {
      issues.add(Issue.error(
          "Multiple weblinks with relation '%s' (%d) found. Expected cardinality: (0..1)".formatted(LICENSE,
              count)));
    }
  }

  /**
   * Validates the presence and cardinality of relation {@code collection}.
   *
   * <p>
   * For the content resource recipe, {@code collection} is mandatory and must occur exactly once
   * within the selected anchor context.
   * </p>
   *
   * @param recordedRelations map of relation type to occurrence count for the selected anchor context
   * @param issues            list used to record validation issues
   */
  private static void validateCollection(HashMap<String, Integer> recordedRelations,
      ArrayList<Issue> issues) {
    validatePresenceOfMandatoryRelation(recordedRelations, issues, COLLECTION);
    var count = recordedRelations.getOrDefault(COLLECTION, 0);
    if (count > 1) {
      issues.add(Issue.error(
          "Multiple weblinks with relation '%s' (%d) found. Expected cardinality: (1)".formatted(COLLECTION,
              count)));
    }
  }

  /**
   * Validates the cardinality of relation {@code cite-as}.
   *
   * <p>
   * For the content resource recipe, {@code cite-as} is optional but must not occur more than once
   * within the selected anchor context.
   * </p>
   *
   * @param recordedRelations map of relation type to occurrence count for the selected anchor context
   * @param issues            list used to record validation issues
   */
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
