package life.qbic.compass.model;

import java.net.URI;

/**
 * <interface short description>
 *
 * @since <version tag>
 */
public sealed interface SameOriginView permits ContentResourceView{

  URI origin();
  SignPostingView signPostingView();
}
