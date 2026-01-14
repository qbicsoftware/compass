package life.qbic.compass.model;

import java.net.URI;
import java.util.List;
import life.qbic.linksmith.model.WebLink;

/**
 * Represents a validated FAIR Signposting <strong>content resource</strong>
 * context (Level&nbsp;2).
 *
 * <p>
 * A content resource typically represents the actual data or files
 * associated with a scholarly object and is usually linked from a landing
 * page using {@code rel=item}.
 * </p>
 *
 * <p>
 * Content resources commonly link back to their landing page using
 * {@code rel=collection}.
 * </p>
 *
 * <p>
 * All WebLinks in this view share the same {@link #origin()} and were
 * validated according to the Level&nbsp;2 content resource recipe.
 * </p>
 *
 * @param origin
 *     the content resource origin URI
 * @param webLinks
 *     all WebLinks belonging to this content resource context
 *
 * @since 1.0.0
 */
public record ContentResourceView(URI origin, List<WebLink> webLinks) implements SameOriginView {

  @Override
  public SignPostingView signPostingView() {
    return null;
  }
}
