package life.qbic.compass.model;

import java.net.URI;
import java.util.List;
import life.qbic.linksmith.model.WebLink;

/**
 * Represents a validated FAIR Signposting <strong>landing page</strong>
 * resource context (Level&nbsp;2).
 *
 * <p>
 * A landing page is the primary entry point for a scholarly object and
 * typically provides:
 * </p>
 *
 * <ul>
 *   <li>a persistent identifier ({@code rel=cite-as}),</li>
 *   <li>links to one or more content resources ({@code rel=item}), and</li>
 *   <li>links to descriptive metadata ({@code rel=describedby}).</li>
 * </ul>
 *
 * <p>
 * All WebLinks in this view share the same {@link #origin()} and were
 * classified as a landing page recipe during Level&nbsp;2 validation.
 * </p>
 *
 * @param origin
 *     the landing page origin URI
 * @param webLinks
 *     all WebLinks belonging to this landing page context
 *
 * @since 1.0.0
 */
public record LandingPageView(URI origin, List<WebLink> webLinks) implements SameOriginView {

  @Override
  public SignPostingView signPostingView() {
    return null;
  }
}
