package life.qbic.compass.model;

import static java.util.Objects.requireNonNull;

import java.util.List;

/**
 * A structured, domain-oriented view over a validated FAIR Signposting
 * <strong>Level&nbsp;2 Link Set</strong>.
 *
 * <p>
 * A {@code Level2LinksetView} represents the result of interpreting a collection
 * of {@link life.qbic.linksmith.model.WebLink WebLinks} according to the
 * Level&nbsp;2 FAIR Signposting recipes.
 * It groups validated links into <em>origin-scoped resource views</em>,
 * where each origin corresponds to a single resource context as defined by
 * the {@code anchor} parameter in RFC&nbsp;8288 / RFC&nbsp;9264.
 * </p>
 *
 * <h2>Contained views</h2>
 *
 * <ul>
 *   <li>{@link LandingPageView} – origins classified as landing pages</li>
 *   <li>{@link ContentResourceView} – origins classified as content resources</li>
 *   <li>{@link MetadataResourceView} – origins classified as metadata resources</li>
 * </ul>
 *
 * <p>
 * Each view contains all WebLinks that share the same origin and were
 * successfully associated with a concrete Level&nbsp;2 recipe.
 * </p>
 *
 * <h2>Missing origins</h2>
 *
 * <p>
 * WebLinks that lack an {@code anchor} parameter cannot be assigned to a
 * Level&nbsp;2 recipe context.
 * Such links are reported separately via {@link MissingOriginLink} entries
 * and are <em>not</em> included in any recipe view.
 * </p>
 *
 * <h2>Validation relationship</h2>
 *
 * <p>
 * This view is a <strong>derived interpretation</strong> of validated input:
 * </p>
 *
 * <ul>
 *   <li>Its presence implies that Level&nbsp;2 validation was attempted.</li>
 *   <li>Its contents reflect the outcome of heuristic recipe detection
 *       and subsequent recipe-specific validation.</li>
 *   <li>It does <em>not</em> replace {@link SignPostingView}, which always
 *       represents the full, unfiltered set of parsed WebLinks.</li>
 * </ul>
 *
 * <p>
 * Clients are expected to inspect {@link SignPostingResult#issueReport()}
 * alongside this view to assess completeness and correctness.
 * </p>
 *
 * <h2>Immutability</h2>
 *
 * <p>
 * All collections exposed by this record are immutable defensive copies.
 * </p>
 *
 * @param landingPages
 *     validated landing page origins
 * @param contentResources
 *     validated content resource origins
 * @param metadataResources
 *     validated metadata resource origins
 * @param missingOriginLinks
 *     links that could not be assigned to any origin due to missing {@code anchor}
 *
 * @since 1.0.0
 */
public record Level2LinksetView(
    List<LandingPageView> landingPages,
    List<ContentResourceView> contentResources,
    List<MetadataResourceView> metadataResources,
    List<MissingOriginLink> missingOriginLinks
) {

  public Level2LinksetView {
    landingPages = List.copyOf(requireNonNull(landingPages));
    contentResources = List.copyOf(requireNonNull(contentResources));
    metadataResources = List.copyOf(requireNonNull(metadataResources));
    missingOriginLinks = List.copyOf(requireNonNull(missingOriginLinks));
  }
}
