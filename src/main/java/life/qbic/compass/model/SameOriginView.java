package life.qbic.compass.model;

import java.net.URI;

/**
 * Common abstraction for Level&nbsp;2 FAIR Signposting views that represent
 * a single <em>origin-scoped resource context</em>.
 *
 * <p>
 * In FAIR Signposting Level&nbsp;2, links are grouped by their
 * <strong>origin</strong>, which is expressed using the {@code anchor}
 * parameter on RFC&nbsp;8288 Web Links.
 * A {@code SameOriginView} guarantees that all contained WebLinks share
 * the same origin.
 * </p>
 *
 * <h2>Domain contract</h2>
 *
 * <ul>
 *   <li>{@link #origin()} must be non-null.</li>
 *   <li>All WebLinks represented by {@link #signPostingView()} must have
 *       an {@code anchor} value equal to {@link #origin()}.</li>
 *   <li>Implementations are immutable and read-only.</li>
 * </ul>
 *
 * <p>
 * This interface enables clients to treat landing pages, content resources,
 * and metadata resources uniformly when iterating over Level&nbsp;2 results,
 * while still allowing recipe-specific APIs on concrete view types.
 * </p>
 *
 * @since 1.0.0
 */
public sealed interface SameOriginView permits LandingPageView, MetadataResourceView, ContentResourceView {

  /**
   * Returns the origin URI that defines the resource context for this view.
   *
   * @return the common origin of all contained WebLinks
   */
  URI origin();

  /**
   * Returns a {@link SignPostingView} over all WebLinks belonging to this origin.
   *
   * <p>
   * The returned view is a semantic convenience wrapper and does not imply
   * additional validation beyond what has already been performed.
   * </p>
   *
   * @return a non-null {@link SignPostingView} for this origin
   */
  SignPostingView signPostingView();
}
