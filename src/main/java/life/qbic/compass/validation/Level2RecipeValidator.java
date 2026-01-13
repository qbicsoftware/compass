package life.qbic.compass.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import life.qbic.compass.model.SignPostingView;
import life.qbic.compass.spi.SignPostingResult;
import life.qbic.compass.spi.SignPostingValidator;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;
import life.qbic.linksmith.spi.WebLinkValidator.IssueReport;

/**
 * Routes Level 2 FAIR Signposting validation to the appropriate recipe validator per anchor context.
 *
 * <p>
 * In FAIR Signposting Level 2, typed links for multiple resources (e.g., landing page, content
 * resources, metadata resources) are commonly provided together via a Link Set (RFC 9264).
 * Each resource is represented by a <em>link context object</em>, which is mapped to RFC 8288 style
 * {@link WebLink}s using the {@code anchor} parameter as the <em>origin</em> (context) of a link.
 * </p>
 *
 * <h2>What this validator does</h2>
 * <ul>
 *   <li><strong>Normalizes input</strong> by filtering {@code null} elements and reporting them as errors.</li>
 *   <li><strong>Groups</strong> all non-null links by their {@code anchor} value (one anchor = one resource context).</li>
 *   <li><strong>Determines the recipe</strong> per anchor context using lightweight heuristics and
 *       delegates validation to one of:
 *     <ul>
 *       <li>{@code Level2LandingPageValidator}</li>
 *       <li>{@code Level2MetadataResourceValidator}</li>
 *       <li>{@code Level2ContentResourceValidator}</li>
 *     </ul>
 *   </li>
 *   <li><strong>Aggregates issues</strong> from delegated validators into one {@link IssueReport}.</li>
 *   <li><strong>Returns a non-destructive view</strong> ({@link SignPostingView}) over all non-null
 *       input links (regardless of validation outcome).</li>
 * </ul>
 *
 * <h2>Contract and policy</h2>
 *
 * <h3>Input requirements</h3>
 * <ul>
 *   <li>{@code webLinks} must not be {@code null}. A {@link NullPointerException} is thrown otherwise.</li>
 *   <li>{@code webLinks} may contain {@code null} elements.
 *       Such elements are <strong>skipped</strong> and an {@code ERROR} issue is recorded that
 *       includes the element index.</li>
 * </ul>
 *
 * <h3>Anchor handling</h3>
 * <ul>
 *   <li>Only links with a present {@code anchor} value participate in recipe routing and validation.</li>
 *   <li>Links without {@code anchor} are retained in the returned {@link SignPostingView} (non-destructive),
 *       but they are <strong>not</strong> validated by this validator, because a Level 2 recipe context
 *       cannot be established without an origin.</li>
 * </ul>
 *
 * <h3>Recipe determination heuristics</h3>
 * <p>
 * Recipe detection is intentionally lightweight and relies on the presence of relation types
 * that are characteristic for the corresponding recipe context.
 * </p>
 * <ul>
 *   <li><strong>Landing page</strong> is assumed if any link in the anchor group contains
 *       {@code rel=cite-as} or {@code rel=item}.</li>
 *   <li><strong>Metadata resource</strong> is assumed if any link contains {@code rel=describes}.</li>
 *   <li><strong>Content resource</strong> is assumed if any link contains {@code rel=collection}.</li>
 * </ul>
 *
 * <p>
 * If none of these signals are present in an anchor group, the recipe is treated as unknown and an
 * {@code ERROR} is recorded for that anchor.
 * </p>
 *
 * <h3>Delegation safety</h3>
 * <ul>
 *   <li>Delegated validators are expected to return a non-null {@link SignPostingResult}.</li>
 *   <li>If a delegated validator returns {@code null}, this validator records a dedicated {@code ERROR}
 *       (see {@link #NULL_SIGNPOSTING_RESULT_ERROR}) and continues processing other anchors.</li>
 * </ul>
 *
 * <h3>Side effects</h3>
 * <ul>
 *   <li>This validator does not mutate the input list.</li>
 *   <li>This validator does not dereference URIs or perform any network I/O.</li>
 *   <li>The returned {@link SignPostingView} is created from a filtered list of non-null links, preserving
 *       their original order among non-null elements.</li>
 * </ul>
 *
 * <h2>Client responsibilities</h2>
 * <ul>
 *   <li>Clients that require validation of links without {@code anchor} must handle them separately
 *       (e.g., treat them as Level 1 inline links or report them as input violations).</li>
 *   <li>Recipe detection is heuristic-based. If clients need strict classification, they should either:
 *     <ul>
 *       <li>pre-group and pre-classify anchor contexts before calling this validator, or</li>
 *       <li>use specialized validators directly where the recipe type is already known.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @since 1.0.0
 * @author Sven Fillinger
 */
public class Level2RecipeValidator implements SignPostingValidator {

  /**
   * Error message template used when a delegated validator unexpectedly returns {@code null}.
   * <p>
   * The {@code %s} placeholder is formatted with the anchor for which validation was attempted.
   */
  public static final String NULL_SIGNPOSTING_RESULT_ERROR = "Validator returned null SignPostingResult for anchor '%s'";
  private final SignPostingValidator landingPageValidator;
  private final SignPostingValidator metadataResourceValidator;
  private final SignPostingValidator contentResourceValidator;

  private Level2RecipeValidator(
      SignPostingValidator landingPageValidator,
      SignPostingValidator metadataResourceValidator,
      SignPostingValidator contentResourceValidator
  ) {
    this.landingPageValidator = Objects.requireNonNull(landingPageValidator);
    this.metadataResourceValidator = Objects.requireNonNull(metadataResourceValidator);
    this.contentResourceValidator = Objects.requireNonNull(contentResourceValidator);
  }

  /**
   * Creates a recipe validator using the default Level 2 recipe validators for landing page,
   * metadata resource, and content resource.
   *
   * @return a fully configured {@link Level2RecipeValidator}
   */
  public static Level2RecipeValidator create() {
    return new Level2RecipeValidator(
        Level2LandingPageValidator.create(),
        Level2MetadataResourceValidator.create(),
        Level2ContentResourceValidator.create()
    );
  }

  /**
   * Creates a recipe validator with user-provided validators for each recipe type.
   *
   * <p>
   * This factory method is primarily intended for testing and advanced customization.
   * Provided validators must be non-null and must adhere to the {@link SignPostingValidator}
   * contract (in particular: returning a non-null {@link SignPostingResult}).
   * </p>
   *
   * @param landingPageValidator       validator for the landing page recipe
   * @param metadataResourceValidator  validator for the metadata resource recipe
   * @param contentResourceValidator   validator for the content resource recipe
   * @return a configured {@link Level2RecipeValidator}
   * @throws NullPointerException if any validator is {@code null}
   */
  static Level2RecipeValidator create(
      SignPostingValidator landingPageValidator,
      SignPostingValidator metadataResourceValidator,
      SignPostingValidator contentResourceValidator
  ) {
    return new Level2RecipeValidator(
        landingPageValidator,
        metadataResourceValidator,
        contentResourceValidator);
  }


  /**
   * Validates a mixed collection of Level 2 Signposting {@link WebLink}s by routing them per
   * anchor context to recipe-specific validators.
   *
   * <p>
   * The method performs three phases:
   * </p>
   * <ol>
   *   <li>Filters {@code null} list elements and records an {@code ERROR} for each skipped element.</li>
   *   <li>Groups remaining links by {@code anchor} (only links with present anchors are grouped).</li>
   *   <li>Determines the recipe for each anchor group and delegates validation.</li>
   * </ol>
   *
   * <p>
   * The returned {@link SignPostingResult} always contains a {@link SignPostingView} over all non-null
   * links and an {@link IssueReport} aggregating all recorded issues.
   * </p>
   *
   * @param webLinks the input list of weblinks to validate (may contain {@code null} elements)
   * @return the validation result including a non-destructive view over all non-null input links
   * @throws NullPointerException if {@code webLinks} is {@code null}
   */
  @Override
  public SignPostingResult validate(List<WebLink> webLinks) {
    Objects.requireNonNull(webLinks);
    var issues = new ArrayList<Issue>();
    var nonNullLinks = new ArrayList<WebLink>();
    WebLink currentWebLink;
    // 1. Filter out null elements and report them as issues with the element's index
    for (int index = 0; index < webLinks.size(); index++) {
      currentWebLink = webLinks.get(index);
      if (currentWebLink == null) {
        issues.add(Issue.error("Input list of weblinks contained null element at index %d".formatted(index)));
        continue;
      }
      nonNullLinks.add(currentWebLink);
    }

    // 2. Group all weblinks by their anchor value => one recipe per origin
    var groupedByAnchor = groupByAnchor(nonNullLinks);
    for (var entrySet : groupedByAnchor.entrySet()) {
      validateRecipe(entrySet.getKey(), entrySet.getValue(), issues);
    }
    return new SignPostingResult(new SignPostingView(nonNullLinks), new IssueReport(issues));
  }

  /**
   * Determines the appropriate recipe validator for a single anchor context and records issues
   * produced by the delegated validator.
   *
   * <p>
   * If the recipe cannot be determined (none of the heuristic signals match), an {@code ERROR}
   * is recorded for the given anchor.
   * </p>
   *
   * @param anchor the anchor (origin) representing the resource context
   * @param links  the links belonging to the anchor context
   * @param issues the shared issue sink collecting all validation findings
   */
  private void validateRecipe(String anchor, List<WebLink> links, ArrayList<Issue> issues) {
    if (looksLikeLandingPage(links)) {
      validateAndSafelyRecordIssues(() -> landingPageValidator.validate(links),
          issues,
          () -> Issue.error(
              NULL_SIGNPOSTING_RESULT_ERROR.formatted(anchor)));
      return;
    }
    if (looksLikeMetadataResource(links)) {
      validateAndSafelyRecordIssues(() -> metadataResourceValidator.validate(links),
          issues,
          () -> Issue.error(
              NULL_SIGNPOSTING_RESULT_ERROR.formatted(anchor)));
      return;
    }
    if (looksLikeContentResource(links)) {
      validateAndSafelyRecordIssues(() -> contentResourceValidator.validate(links),
          issues,
          () -> Issue.error(
              NULL_SIGNPOSTING_RESULT_ERROR.formatted(anchor)));
      return;
    }
    issues.add(Issue.error("Unknown FAIR Signposting recipe for anchor '%s'".formatted(anchor)));
  }

  /**
   * Executes a delegated validator call and appends its issues to the shared issue list.
   *
   * <p>
   * This method hardens delegation by handling unexpected {@code null} results:
   * if the supplied validator returns {@code null}, {@code nullHandler} is used to produce an
   * {@link Issue} that is appended to {@code issues}.
   * </p>
   *
   * <p>
   * Note: This method assumes that a non-null {@link SignPostingResult} contains a non-null
   * {@link IssueReport}. If a delegated validator violates that contract, a {@link NullPointerException}
   * may still occur and should be considered a bug in the delegated validator.
   * </p>
   *
   * @param validator   supplier performing validation and returning a {@link SignPostingResult}
   * @param issues      shared sink to append issues to
   * @param nullHandler supplier producing an issue if {@code validator.get()} returns {@code null}
   */
  private static void validateAndSafelyRecordIssues(
      Supplier<SignPostingResult> validator,
      List<Issue> issues,
      Supplier<Issue> nullHandler) {
    var result = validator.get();
    if (result == null) {
      issues.add(nullHandler.get());
      return;
    }
    issues.addAll(result.issueReport().issues());
  }

  /**
   * Groups weblinks by their {@code anchor} value.
   *
   * <p>
   * Only links with a present anchor are included in the returned map. Links without anchors are
   * ignored for grouping because they cannot be assigned to a Level 2 recipe context.
   * </p>
   *
   * @param weblinks input links (expected to be non-null elements)
   * @return a map of anchor string to list of links sharing that anchor
   */
  private static Map<String, List<WebLink>> groupByAnchor(List<WebLink> weblinks) {
    var linksWithAnchor = weblinks.stream()
        .filter(link -> link.anchor().isPresent()).toList();
    // TODO report missing anchor values to the client!
    return linksWithAnchor.stream()
        .collect(Collectors.groupingBy(link -> link.anchor().orElse("")));
  }

  /**
   * Heuristic check for identifying a landing page recipe within an anchor group.
   *
   * <p>
   * Current policy: if any link contains {@code rel=cite-as} or {@code rel=item}, the group is
   * treated as a landing page recipe.
   * </p>
   *
   * @param recipe the links for one anchor context
   * @return true if the group is classified as landing page recipe
   */
  private static boolean looksLikeLandingPage(List<WebLink> recipe) {
    for (WebLink link : recipe) {
      if (link == null) {
        continue;
      }
      if (link.rel().contains("cite-as") || link.rel().contains("item")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Heuristic check for identifying a metadata resource recipe within an anchor group.
   *
   * <p>
   * Current policy: if any link contains {@code rel=describes}, the group is treated as a metadata
   * resource recipe.
   * </p>
   *
   * @param recipe the links for one anchor context
   * @return true if the group is classified as metadata resource recipe
   */
  private static boolean looksLikeMetadataResource(List<WebLink> recipe) {
    for (WebLink link : recipe) {
      if (link == null) {
        continue;
      }
      if (link.rel().contains("describes")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Heuristic check for identifying a content resource recipe within an anchor group.
   *
   * <p>
   * Current policy: if any link contains {@code rel=collection}, the group is treated as a content
   * resource recipe.
   * </p>
   *
   * @param recipe the links for one anchor context
   * @return true if the group is classified as content resource recipe
   */
  private static boolean looksLikeContentResource(List<WebLink> recipe) {
    for (WebLink link : recipe) {
      if (link == null) {
        continue;
      }
      if (link.rel().contains("collection")) {
        return true;
      }
    }
    return false;
  }
}
