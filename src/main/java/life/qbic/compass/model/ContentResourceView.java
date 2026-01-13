package life.qbic.compass.model;

import java.net.URI;
import java.util.List;
import life.qbic.linksmith.model.WebLink;

/**
 * <record short description>
 *
 * @since <version tag>
 */
public record ContentResourceView(List<WebLink> webLinks) implements SameOriginView {

  @Override
  public URI origin() {
    return null;
  }

  @Override
  public SignPostingView signPostingView() {
    return null;
  }
}
