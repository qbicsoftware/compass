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
 * Validates a Level 2 FAIR Signposting <em>metadata resource recipe</em> over a collection of
 * {@link WebLink}s.
 *
 * <h2>What this validator checks</h2>
 * <p>
 * FAIR Signposting Level 2 uses a Link Set (e.g. {@code application/linkset+json}) to provide a
 * comprehensive set of typed links for multiple resources. Within that set, each recipe is
 * validated <strong>per origin (anchor context)</strong>.
 * </p>
 *
 * <p>
 * This validator implements the <strong>metadata resource</strong> recipe at Level 2. In that
 * recipe, the metadata resource (the "anchor") must point back to the landing page via
 * {@code rel=describes} and must do so with the expected cardinality.
 * </p>
 *
 * <h2>Precondition: single anchor context</h2>
 * <p>
 * The validator does <strong>not</strong> assume that the passed list has already been grouped by
 * anchor. Therefore, it first enforces that the input can be interpreted as a single
 * metadata-resource context:
 * </p>
 * <ul>
 *   <li>All non-null WebLinks must have an {@code anchor} value.</li>
 *   <li>All anchors must be identical (a single origin context).</li>
 * </ul>
 *
 * <p>
 * If the input violates these context requirements, validation is considered <em>ambiguous</em> and
 * the validator will record issues and abort further recipe checks (i.e. it will not report
 * "missing describes" if it cannot reliably determine the context).
 * </p>
 *
 * <h2>Recipe requirements validated</h2>
 * <ul>
 *   <li><strong>Mandatory</strong>: at least one {@code rel=describes} must be present.</li>
 *   <li><strong>Cardinality</strong>: {@code rel=describes} must not occur more than once for the
 *       validated anchor context (expected cardinality: exactly 1).</li>
 * </ul>
 *
 * <h2>Null handling and client contract</h2>
 * <ul>
 *   <li><strong>{@code webLinks == null}</strong>:
 *       this implementation will currently throw a {@link NullPointerException} (because the input
 *       list is used without a null guard). Clients must pass a non-null list.</li>
 *   <li><strong>null elements inside the list</strong>:
 *       null items are treated as invalid input. The underlying {@link Level2Util} is expected
 *       to record an {@link Issue#error(String)} including the index and then continue scanning.
 *       (No {@link NullPointerException} should be thrown for null items.)</li>
 *   <li><strong>missing anchor values</strong>:
 *       WebLinks without an {@code anchor} are collected and recorded as errors, and validation
 *       aborts early (no further cardinality evaluation).</li>
 * </ul>
 *
 * <p>
 * The returned {@link SignPostingResult} always contains a {@link SignPostingView} created from the
 * original input list; no filtering, mutation, dereferencing, or remote retrieval is performed.
 * </p>
 *
 * @author Sven Fillinger
 * @since 1.0.0
 */
public class Level2MetadataResourceValidator implements SignPostingValidator {

  /**
   * The relation type required by the Level 2 metadata resource recipe.
   * <p>
   * A metadata resource must contain a {@code rel=describes} link that points to the landing page
   * it describes.
   * </p>
   */
  public static final String DESCRIBES = "describes";

  private Level2MetadataResourceValidator() {
  }

  /**
   * Factory method to create a new validator instance.
   * <p>
   * The validator is stateless; callers may reuse the returned instance.
   * </p>
   *
   * @return a new {@link SignPostingValidator} implementing the Level 2 metadata resource recipe
   */
  public static SignPostingValidator create() {
    return new Level2MetadataResourceValidator();
  }

  /**
   * Validates the provided WebLinks against the Level 2 metadata resource Signposting recipe.
   *
   * <p>
   * The validator:
   * </p>
   * <ol>
   *   <li>checks that the input can be interpreted as a single anchor context
   *       (no missing anchors, no mixed anchors),</li>
   *   <li>records issues for ambiguous/invalid context,</li>
   *   <li>and only if the context is valid, validates mandatory relations and cardinalities.</li>
   * </ol>
   *
   * <p>
   * No network access is performed. The validator inspects only the supplied WebLinks.
   * </p>
   *
   * @param webLinks the WebLinks to validate (must not be {@code null})
   * @return the signposting validation result containing a view over the original WebLinks and an
   * issue report
   * @throws NullPointerException if {@code webLinks} is {@code null}
   */
  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    Objects.requireNonNull(webLinks);
    var issues = new ArrayList<Issue>();
    validateForMetadataResource(webLinks, issues);

    return new SignPostingResult(
        new SignPostingView(
            webLinks.stream()
                .filter(Objects::nonNull)
                .toList()),
        new IssueReport(issues));
  }

  /**
   * Performs validation for the metadata resource recipe.
   *
   * <p>
   * This method enforces the Level 2 precondition of a single anchor context using
   * {@link Level2Util#validateForSingleAnchor(List, List, List, Map)}.
   * </p>
   *
   * <p>
   * If the context is ambiguous (multiple anchors) or incomplete (missing anchors), issues are
   * recorded and recipe validation is aborted early.
   * </p>
   *
   * @param webLinks the input WebLinks (must not be {@code null})
   * @param issues   the mutable list used to record errors and warnings
   */
  private static void validateForMetadataResource(List<WebLink> webLinks, List<Issue> issues) {
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

    validateDescribes(recordedRelations, issues);
  }

  /**
   * Validates the {@code rel=describes} requirement for a metadata resource.
   *
   * <p>
   * Requirements enforced:
   * </p>
   * <ul>
   *   <li>{@code describes} must be present (mandatory relation).</li>
   *   <li>{@code describes} must not occur more than once (expected cardinality: exactly 1).</li>
   * </ul>
   *
   * @param recordedRelations relation counts collected for the selected anchor context
   * @param issues            the list used to record validation issues
   */
  private static void validateDescribes(Map<String, Integer> recordedRelations,
      List<Issue> issues) {
    validatePresenceOfMandatoryRelation(recordedRelations, issues, DESCRIBES);
    var count = recordedRelations.getOrDefault(DESCRIBES, 0);
    var expectedCardinality = 1;
    if (count > expectedCardinality) {
      issues.add(Issue.error(
          "Multiple links with relation type '%s' found. Expected a cardinality of (%d)".formatted(
              DESCRIBES, expectedCardinality)));
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
