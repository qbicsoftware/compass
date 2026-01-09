package life.qbic.compass.validation;

import java.util.List;
import java.util.Map;
import life.qbic.linksmith.model.WebLink;
import life.qbic.linksmith.spi.WebLinkValidator.Issue;

/**
 * Utility functions shared by FAIR Signposting Level 2 validators.
 *
 * <p>
 * This class provides common precondition checks required by all Level 2 Signposting recipes
 * (Landing Page, Metadata Resource, Content Resource). In particular, it validates that a
 * collection of {@link WebLink}s represents a <strong>single, unambiguous link context</strong>.
 * </p>
 *
 * <h2>Conceptual background</h2>
 * <p>
 * In FAIR Signposting Level 2 (RFC 9264), links are expressed in a <em>link set</em> and grouped by
 * their {@code anchor}, which represents the origin resource for which a recipe is defined.
 * </p>
 *
 * <p>
 * A Level 2 recipe (e.g. Landing Page) can only be validated if:
 * </p>
 * <ul>
 *   <li>all links belong to the <em>same anchor</em>, and</li>
 *   <li>every link explicitly declares its {@code anchor}.</li>
 * </ul>
 *
 * <p>
 * If either condition is violated, the recipe is considered
 * <strong>not safely verifiable</strong>, and further validation must be skipped.
 * </p>
 *
 * <h2>Design intent</h2>
 * <ul>
 *   <li>This class performs <strong>structural precondition checks only</strong>.</li>
 *   <li>It does <strong>not</strong> validate relation cardinalities or recipe completeness.</li>
 *   <li>It allows validators to fail fast before emitting misleading errors.</li>
 * </ul>
 *
 * <p>
 * This class is stateless and thread-safe.
 * </p>
 *
 * @author Sven Fillinger
 * @since 1.0.0
 */
final class Level2Util {

  private Level2Util() {
  }

  /**
   * Validates that the provided WebLinks form a single, well-defined Level 2 link context.
   *
   * <p>
   * This method enforces the following invariants:
   * </p>
   * <ol>
   *   <li>All {@link WebLink}s that declare an {@code anchor} must declare the <em>same</em> anchor value.</li>
   *   <li>Any WebLink missing an {@code anchor} is recorded but does not immediately fail validation.</li>
   *   <li>Relation types ({@code rel}) are counted only for links with the selected anchor.</li>
   * </ol>
   *
   * <p>
   * If multiple distinct anchor values are encountered, validation fails immediately,
   * because the recipe context becomes ambiguous.
   * </p>
   *
   * <h3>Side effects</h3>
   * <ul>
   *   <li>{@code issues} may receive an error describing anchor ambiguity.</li>
   *   <li>{@code missingAnchor} collects links without an {@code anchor}.</li>
   *   <li>{@code relationsCount} is populated with relation frequencies
   *       <em>only if</em> all anchors are consistent.</li>
   * </ul>
   *
   * <h3>Caller responsibilities</h3>
   * <ul>
   *   <li>Decide whether missing-anchor links should abort validation or merely be reported.</li>
   *   <li>Stop further recipe validation if this method returns {@code false}.</li>
   *   <li>Interpret {@code relationsCount} according to the specific recipe being validated.</li>
   * </ul>
   *
   * <p>
   * This method does not:
   * </p>
   * <ul>
   *   <li>verify mandatory relations,</li>
   *   <li>check relation cardinalities,</li>
   *   <li>infer resource type (landing/metadata/content), or</li>
   *   <li>dereference any link targets.</li>
   * </ul>
   *
   * @param webLinks       the WebLinks to validate as a single Level 2 context
   * @param issues         a mutable list used to record validation issues
   * @param missingAnchor  a mutable list that will collect all WebLinks without an {@code anchor}
   * @param relationsCount a mutable map that will be populated with relation-type occurrence
   *                       counts
   * @return {@code true} if all WebLinks share a single anchor context; {@code false} if multiple
   * iple distinct anchors are detected
   */
  static boolean validateForSingleAnchor(
      List<WebLink> webLinks,
      List<Issue> issues,
      List<WebLink> missingAnchor,
      Map<String, Integer> relationsCount) {
    AnchorHolder selectedAnchor = new AnchorHolder();
    WebLink currentLink;
    for (int index = 0; index < webLinks.size(); index++) {
      currentLink = webLinks.get(index);
      if (!processLink(currentLink, index, selectedAnchor, issues, missingAnchor, relationsCount)) {
        return false;
      }
    }
    return true;
  }

  private static boolean processLink(WebLink currentLink,
      int index,
      AnchorHolder selectedAnchor,
      List<Issue> issues,
      List<WebLink> missingAnchor,
      Map<String, Integer> relationsCount) {
    if (currentLink == null) {
      issues.add(Issue.error("Skipped null value for weblink at index %d".formatted(index)));
      return true;
    }
    var currentAnchor = currentLink.anchor().orElse(null);
    if (currentAnchor == null) {
      missingAnchor.add(currentLink);
      // Return early, no need to validate a link with unknown context
      return true;
    }

    // Set the first available anchor value as selected for this context
    if (selectedAnchor.value == null) {
      selectedAnchor.value = currentAnchor;
    }
    // Check for equal anchors. In case of different anchors the validation fails, since
    // we cannot reliably determine the completeness of a Landing Page recipe
    if (!currentAnchor.equals(selectedAnchor.value)) {
      issues.add(Issue.error(
          "Input contains multiple anchors; context is ambiguous. Found new anchor '%s' but expected '%s'".formatted(
              currentAnchor, selectedAnchor)));
      // We can stop validation, since without a single origin, we cannot reliably validate the Landing Page recipe
      return false;
    }

    currentLink.rel().forEach(rel -> {
      var currentCount = relationsCount.getOrDefault(rel, 0);
      relationsCount.put(rel, currentCount + 1);
    });

    return true;
  }

  private static final class AnchorHolder {
    String value;
  }
}
