package life.qbic.jsign.validator;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import life.qbic.linksmith.model.WebLink;

/**
 * <class short description>
 *
 * @since <version tag>
 */
public record SignPostingView(List<WebLink> webLinks) {

  public SignPostingView {
    Objects.requireNonNull(webLinks);
    webLinks = List.copyOf(webLinks);
  }

  /**
   * Lists all web links that have the relation type parameter with the given value.
   *
   * <pre>
   * {@code
   * // Examples:
   * // for links with 'rel=item'
   * withRelationType("item")
   * // for links with 'rel=author'
   * withRelationType("author")
   * }
   * </pre>
   *
   * @param type the relation type to search for
   * @return a list of web links with the given relation type
   */
  public List<WebLink> withRelationType(String type) {
    return SignPostingView.withRelationType(webLinks, type);
  }

  private static List<WebLink> withRelationType(List<WebLink> webLinks, String type) {
    return webLinks.stream()
        .filter(Objects::nonNull)
        .filter(link -> hasRelationType(link, type))
        .toList();
  }

  private static boolean hasRelationType(WebLink webLink, String type) {
    return webLink.rel().contains(type.toLowerCase());
  }

  /**
   * Lists all web links that have the relation type {@code rel=cite-as}.
   *
   * @return a list of web links with the relation type 'cite-as'
   */
  public List<URI> citeAs() {
    return withRelationType("cite-as").stream()
        .map(WebLink::target)
        .toList();
  }

  /**
   * Lists all web links that have the relation type {@code rel=describedby}.
   *
   * @return a list of web links with the relation type 'describedby'
   */
  public List<URI> describedBy() {
    return withRelationType("describedby").stream()
        .map(WebLink::target)
        .toList();
  }

  /**
   * Lists all web links that have the relation type {@code rel=linkset}.
   *
   * @return a list of web links with the relation type 'linkset'
   */
  public List<URI> linksets() {
    return withRelationType("linkset").stream()
        .map(WebLink::target)
        .toList();
  }
}
