package life.qbic.compass.model;

import java.net.URI;
import java.util.List;
import life.qbic.linksmith.model.WebLink;

/**
 * Represents a validated FAIR Signposting <strong>metadata resource</strong>
 * context (Level&nbsp;2).
 *
 * <p>
 * A metadata resource provides structured descriptive information
 * about a scholarly object and typically links to the resource it
 * describes using {@code rel=describes}.
 * </p>
 *
 * <p>
 * All WebLinks in this view share the same {@link #origin()} and were
 * validated according to the Level&nbsp;2 metadata resource recipe.
 * </p>
 *
 * @param origin
 *     the metadata resource origin URI
 * @param webLinks
 *     all WebLinks belonging to this metadata resource context
 *
 * @since 1.0.0
 */
public record MetadataResourceView(URI origin, List<WebLink> webLinks) implements SameOriginView {

  @Override
  public SignPostingView signPostingView() {
    return null;
  }
}
